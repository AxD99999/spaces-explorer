import { ActiveSpaceTask, HfSpace, GeneratedMediaItem } from '../types';
import { runSpaceEndpoint, uploadSpaceFiles } from './api';
import { extractMediaItemsFromOutput, saveMediaItem } from './mediaStorage';

export interface PreservedSpaceState {
  formValues: Record<string, any>;
  imagePreviews: Record<string, string>;
  multiImagePreviews: Record<string, string[]>;
  selectedEndpoint?: string;
  outputResult?: any;
  runError?: string | null;
  isRunning?: boolean;
}

type TaskListener = (tasks: ActiveSpaceTask[]) => void;
type CompletionListener = (task: ActiveSpaceTask, mediaItems: GeneratedMediaItem[]) => void;

class TaskManager {
  private tasks: Map<string, ActiveSpaceTask> = new Map();
  private spaceStates: Map<string, PreservedSpaceState> = new Map();
  private abortControllers: Map<string, AbortController> = new Map();
  private listeners: Set<TaskListener> = new Set();
  private completionListeners: Set<CompletionListener> = new Set();
  private timerInterval: any = null;

  constructor() {
    // Start background ticker for active tasks elapsed time
    if (typeof window !== 'undefined') {
      this.timerInterval = setInterval(() => {
        let hasRunning = false;
        const now = Date.now();

        this.tasks.forEach((task) => {
          if (task.status === 'running') {
            hasRunning = true;
            task.elapsedSeconds = +((now - task.startTime) / 1000).toFixed(1);
          }
        });

        if (hasRunning) {
          this.notifyListeners();
        }
      }, 200);
    }
  }

  public subscribe(listener: TaskListener): () => void {
    this.listeners.add(listener);
    listener(this.getAllTasks());
    return () => {
      this.listeners.delete(listener);
    };
  }

  public onComplete(listener: CompletionListener): () => void {
    this.completionListeners.add(listener);
    return () => {
      this.completionListeners.delete(listener);
    };
  }

  private notifyListeners() {
    const list = this.getAllTasks();
    this.listeners.forEach((l) => l(list));
  }

  public getAllTasks(): ActiveSpaceTask[] {
    return Array.from(this.tasks.values()).sort((a, b) => b.startTime - a.startTime);
  }

  public getRunningTasks(): ActiveSpaceTask[] {
    return this.getAllTasks().filter((t) => t.status === 'running');
  }

  public getRunningCount(): number {
    return this.getRunningTasks().length;
  }

  public getTask(taskId: string): ActiveSpaceTask | undefined {
    return this.tasks.get(taskId);
  }

  public getTaskForSpace(spaceId: string): ActiveSpaceTask | undefined {
    return Array.from(this.tasks.values()).find(
      (t) => t.space.id === spaceId && t.status === 'running'
    );
  }

  // Preserve state of a space across navigation
  public saveSpaceState(spaceId: string, updates: Partial<PreservedSpaceState>) {
    const current = this.spaceStates.get(spaceId) || {
      formValues: {},
      imagePreviews: {},
      multiImagePreviews: {},
    };
    this.spaceStates.set(spaceId, { ...current, ...updates });
  }

  public getSpaceState(spaceId: string): PreservedSpaceState | undefined {
    return this.spaceStates.get(spaceId);
  }

  // Start executing a space in the background
  public async startTask(
    space: HfSpace,
    endpoint: string,
    formValues: Record<string, any>,
    imagePreviews: Record<string, string>,
    multiImagePreviews: Record<string, string[]>,
    payloadData: any[],
    activeToken?: string,
    subdomain?: string
  ): Promise<string> {
    const taskId = `task_${space.id.replace(/[^a-zA-Z0-9]/g, '_')}_${Date.now()}`;
    const abortController = new AbortController();
    this.abortControllers.set(taskId, abortController);

    const task: ActiveSpaceTask = {
      id: taskId,
      space,
      endpoint,
      formValues: { ...formValues },
      imagePreviews: { ...imagePreviews },
      multiImagePreviews: { ...multiImagePreviews },
      status: 'running',
      startTime: Date.now(),
      elapsedSeconds: 0,
    };

    this.tasks.set(taskId, task);

    // Save space state immediately
    this.saveSpaceState(space.id, {
      formValues,
      imagePreviews,
      multiImagePreviews,
      selectedEndpoint: endpoint,
      isRunning: true,
      runError: null,
    });

    this.notifyListeners();

    // Run in background
    (async () => {
      try {
        const spaceSubdomain =
          subdomain || space.subdomain || space.id.replace('/', '-').toLowerCase();

        // 1. Process any pending image uploads if needed
        const pendingUploads: { key: string; name: string; base64: string }[] = [];
        Object.entries(formValues).forEach(([k, v]) => {
          if (v && typeof v === 'object' && v.base64) {
            pendingUploads.push({ key: k, name: v.orig_name || 'image.png', base64: v.base64 });
          }
        });

        let uploadedMap: Record<string, string> = {};
        if (pendingUploads.length > 0) {
          try {
            const upRes = await uploadSpaceFiles(
              spaceSubdomain,
              pendingUploads.map((u) => ({ name: u.name, base64: u.base64 })),
              activeToken
            );
            if (upRes.paths && Array.isArray(upRes.paths)) {
              pendingUploads.forEach((u, i) => {
                uploadedMap[u.key] = upRes.paths[i];
              });
            }
          } catch (e) {
            console.warn('Pre-upload skipped, proceeding with payload:', e);
          }
        }

        // Adjust payloadData with uploaded file paths if applicable
        const finalPayload = payloadData.map((item, idx) => {
          const key = `param_${idx}`;
          if (uploadedMap[key]) {
            return {
              path: uploadedMap[key],
              url: `https://${spaceSubdomain}.hf.space/gradio_api/file=${uploadedMap[key]}`,
              orig_name: item.orig_name || 'file.png',
              mime_type: item.mime_type || 'image/png',
              meta: { _type: 'gradio.FileData' },
            };
          }
          return item;
        });

        // 2. Run the endpoint
        const response = await runSpaceEndpoint(
          space.id,
          endpoint,
          finalPayload,
          activeToken,
          spaceSubdomain
        );

        if (!response.success) {
          throw new Error(response.error || 'Execution returned without data');
        }

        const result = response.data;
        task.status = 'completed';
        task.result = result;
        task.elapsedSeconds = +((Date.now() - task.startTime) / 1000).toFixed(1);

        // 3. Extract and permanently save generated media
        // Look for prompt in form values
        let detectedPrompt: string | undefined = undefined;
        Object.values(formValues).forEach((val) => {
          if (typeof val === 'string' && val.length > 3 && !detectedPrompt) {
            detectedPrompt = val;
          }
        });

        const mediaItems = extractMediaItemsFromOutput(
          result,
          space,
          endpoint,
          detectedPrompt,
          task.elapsedSeconds,
          spaceSubdomain
        );

        for (const item of mediaItems) {
          await saveMediaItem(item);
        }

        task.savedMediaIds = mediaItems.map((m) => m.id);

        // Update space state
        this.saveSpaceState(space.id, {
          outputResult: result,
          isRunning: false,
          runError: null,
        });

        this.notifyListeners();

        // Notify completion listeners (for toast notification)
        this.completionListeners.forEach((l) => l(task, mediaItems));
      } catch (err: any) {
        task.status = 'error';
        task.error = err.message || 'Execution failed';
        task.elapsedSeconds = +((Date.now() - task.startTime) / 1000).toFixed(1);

        this.saveSpaceState(space.id, {
          isRunning: false,
          runError: task.error,
        });

        this.notifyListeners();
      } finally {
        this.abortControllers.delete(taskId);
      }
    })();

    return taskId;
  }

  public cancelTask(taskId: string) {
    const task = this.tasks.get(taskId);
    if (task && task.status === 'running') {
      const controller = this.abortControllers.get(taskId);
      if (controller) controller.abort();
      task.status = 'error';
      task.error = 'Execution stopped by user';
      this.saveSpaceState(task.space.id, { isRunning: false });
      this.notifyListeners();
    }
  }

  public removeTask(taskId: string) {
    this.tasks.delete(taskId);
    this.notifyListeners();
  }

  public clearCompletedTasks() {
    this.tasks.forEach((task, id) => {
      if (task.status !== 'running') {
        this.tasks.delete(id);
      }
    });
    this.notifyListeners();
  }
}

export const taskManager = new TaskManager();

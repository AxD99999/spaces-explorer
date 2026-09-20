import React, { useState, useEffect, useRef } from 'react';
import {
  ArrowLeft,
  Play,
  Loader2,
  Sparkles,
  Upload,
  Image as ImageIcon,
  Images,
  X,
  RotateCcw,
  Download,
  Copy,
  Check,
  ExternalLink,
  KeyRound,
  AlertCircle,
  FileVideo,
  FileAudio,
  Code,
  FileText,
  Sliders,
  CheckCircle2,
  StopCircle,
} from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';
import { HfSpace, SpaceEndpointSpec, SpaceEndpointParam, UserApiKey } from '../types';
import { fetchSpaceInfo, runSpaceEndpoint, uploadSpaceFiles } from '../services/api';
import { taskManager } from '../services/taskManager';

interface SpaceRunnerProps {
  space: HfSpace;
  apiKeys: UserApiKey[];
  onBack: () => void;
  onOpenDetails?: (space: HfSpace) => void;
  onOpenApiKeys: () => void;
}

export const SpaceRunner: React.FC<SpaceRunnerProps> = ({
  space,
  apiKeys,
  onBack,
  onOpenDetails,
  onOpenApiKeys,
}) => {
  const [selectedKeyId, setSelectedKeyId] = useState<string>(() => {
    const defaultKey = apiKeys.find((k) => k.isDefault);
    return defaultKey ? defaultKey.id : apiKeys[0]?.id || 'none';
  });

  const [isLoadingSpec, setIsLoadingSpec] = useState(true);
  const [specError, setSpecError] = useState<string | null>(null);
  const [endpoints, setEndpoints] = useState<SpaceEndpointSpec[]>([]);
  const [selectedEndpoint, setSelectedEndpoint] = useState<string>('');
  const [subdomain, setSubdomain] = useState<string>(
    space.subdomain || space.id.replace('/', '-').toLowerCase()
  );

  // Form input states (restored from taskManager preserved state if available)
  const [formValues, setFormValues] = useState<Record<string, any>>(() => {
    return taskManager.getSpaceState(space.id)?.formValues || {};
  });
  const [imagePreviews, setImagePreviews] = useState<Record<string, string>>(() => {
    return taskManager.getSpaceState(space.id)?.imagePreviews || {};
  });
  const [multiImagePreviews, setMultiImagePreviews] = useState<Record<string, string[]>>(() => {
    return taskManager.getSpaceState(space.id)?.multiImagePreviews || {};
  });

  // Execution state (connected to taskManager background jobs)
  const [isRunning, setIsRunning] = useState<boolean>(() => {
    return !!taskManager.getTaskForSpace(space.id);
  });
  const [executionTime, setExecutionTime] = useState<number>(() => {
    const activeTask = taskManager.getTaskForSpace(space.id);
    return activeTask ? activeTask.elapsedSeconds : 0;
  });
  const [runError, setRunError] = useState<string | null>(() => {
    return taskManager.getSpaceState(space.id)?.runError || null;
  });
  const [outputResult, setOutputResult] = useState<any>(() => {
    return taskManager.getSpaceState(space.id)?.outputResult || null;
  });
  const [copiedOutput, setCopiedOutput] = useState(false);

  // Subscribe to background task updates for this space
  useEffect(() => {
    const unsubscribe = taskManager.subscribe((tasks) => {
      const task = tasks.find((t) => t.space.id === space.id);
      if (task) {
        if (task.status === 'running') {
          setIsRunning(true);
          setExecutionTime(task.elapsedSeconds);
        } else if (task.status === 'completed') {
          setIsRunning(false);
          setOutputResult(task.result);
          setExecutionTime(task.elapsedSeconds);
          setRunError(null);
        } else if (task.status === 'error') {
          setIsRunning(false);
          setRunError(task.error || 'Execution failed');
          setExecutionTime(task.elapsedSeconds);
        }
      } else {
        const state = taskManager.getSpaceState(space.id);
        if (state) {
          if (state.isRunning !== undefined) setIsRunning(state.isRunning);
          if (state.outputResult !== undefined) setOutputResult(state.outputResult);
          if (state.runError !== undefined) setRunError(state.runError);
        }
      }
    });
    return unsubscribe;
  }, [space.id]);

  const currentKey = apiKeys.find((k) => k.id === selectedKeyId);
  const activeToken = currentKey ? currentKey.token : undefined;

  const spaceTitle = space.title || space.id.split('/')[1] || space.id;
  const authorName = space.author || space.id.split('/')[0] || 'community';
  const emoji = space.emoji || '🤗';
  const isSleeping = space.runtime?.stage === 'SLEEPING';

  // Fetch live specification
  useEffect(() => {
    let isMounted = true;
    setIsLoadingSpec(true);
    setSpecError(null);

    fetchSpaceInfo(space.id, activeToken)
      .then((res) => {
        if (!isMounted) return;
        if (res.subdomain) setSubdomain(res.subdomain);
        if (res.endpoints && res.endpoints.length > 0) {
          setEndpoints(res.endpoints);
          const savedState = taskManager.getSpaceState(space.id);
          const initialEp = savedState?.selectedEndpoint || res.endpoints[0].name;
          setSelectedEndpoint(initialEp);

          // If no form values were previously saved, initialize defaults
          if (!savedState || Object.keys(savedState.formValues || {}).length === 0) {
            const target = res.endpoints.find((e: SpaceEndpointSpec) => e.name === initialEp) || res.endpoints[0];
            initFormForEndpoint(target);
          }
        } else {
          // Fallback generic endpoint
          setSelectedEndpoint('/predict');
          const savedState = taskManager.getSpaceState(space.id);
          if (!savedState || Object.keys(savedState.formValues || {}).length === 0) {
            initFallbackForm();
          }
        }
      })
      .catch((err) => {
        if (!isMounted) return;
        setSpecError(err.message || 'Failed to load Space API specification');
        const savedState = taskManager.getSpaceState(space.id);
        if (!savedState || Object.keys(savedState.formValues || {}).length === 0) {
          initFallbackForm();
        }
      })
      .finally(() => {
        if (isMounted) setIsLoadingSpec(false);
      });

    return () => {
      isMounted = false;
    };
  }, [space.id, activeToken]);

  const initFormForEndpoint = (ep: SpaceEndpointSpec) => {
    const initial: Record<string, any> = {};
    const imgPrev: Record<string, string> = {};
    const multiPrev: Record<string, string[]> = {};

    ep.parameters.forEach((param, index) => {
      const key = `param_${index}`;
      if (param.parameter_has_default && param.parameter_default !== undefined) {
        initial[key] = param.parameter_default;
      } else if (param.example_input !== undefined && param.example_input !== '...') {
        initial[key] = param.example_input;
      } else {
        const comp = (param.component || '').toLowerCase();
        const pType = (param.python_type?.type || '').toLowerCase();

        if (comp.includes('checkbox') || pType.includes('bool')) {
          initial[key] = false;
        } else if (comp.includes('slider') || pType.includes('int') || pType.includes('float')) {
          initial[key] = 0;
        } else if (comp.includes('image') || pType.includes('filepath')) {
          initial[key] = null;
        } else {
          initial[key] = '';
        }
      }
    });

    setFormValues(initial);
    setImagePreviews(imgPrev);
    setMultiImagePreviews(multiPrev);
  };

  const initFallbackForm = () => {
    setFormValues({
      param_0: 'A cinematic high-resolution portrait of a futuristic cyber cat, 8k',
      param_1: null,
      param_2: 25,
      param_3: 7.5,
    });
  };

  const updateFormValue = (key: string, value: any) => {
    setFormValues((prev) => {
      const updated = { ...prev, [key]: value };
      taskManager.saveSpaceState(space.id, { formValues: updated });
      return updated;
    });
  };

  const handleEndpointChange = (epName: string) => {
    setSelectedEndpoint(epName);
    taskManager.saveSpaceState(space.id, { selectedEndpoint: epName });
    const target = endpoints.find((e) => e.name === epName);
    if (target) {
      initFormForEndpoint(target);
    }
  };

  // Image Upload handler for single image
  const handleSingleImageUpload = (key: string, file: File) => {
    const reader = new FileReader();
    reader.onload = () => {
      const dataUrl = reader.result as string;
      setImagePreviews((prev) => {
        const next = { ...prev, [key]: dataUrl };
        taskManager.saveSpaceState(space.id, { imagePreviews: next });
        return next;
      });
      setFormValues((prev) => {
        const next = {
          ...prev,
          [key]: {
            path: file.name,
            url: dataUrl,
            orig_name: file.name,
            mime_type: file.type,
            size: file.size,
            meta: { _type: 'gradio.FileData' },
            base64: dataUrl,
          },
        };
        taskManager.saveSpaceState(space.id, { formValues: next });
        return next;
      });
    };
    reader.readAsDataURL(file);
  };

  const removeSingleImage = (key: string) => {
    setImagePreviews((prev) => {
      const copy = { ...prev };
      delete copy[key];
      taskManager.saveSpaceState(space.id, { imagePreviews: copy });
      return copy;
    });
    setFormValues((prev) => {
      const next = { ...prev, [key]: null };
      taskManager.saveSpaceState(space.id, { formValues: next });
      return next;
    });
  };

  // Multiple Images upload handler
  const handleMultiImageUpload = (key: string, files: FileList) => {
    const currentFiles = multiImagePreviews[key] || [];
    const newPreviews = [...currentFiles];
    const dataObjects: any[] = formValues[key] && Array.isArray(formValues[key]) ? [...formValues[key]] : [];

    Array.from(files).forEach((file) => {
      const reader = new FileReader();
      reader.onload = () => {
        const dataUrl = reader.result as string;
        newPreviews.push(dataUrl);
        dataObjects.push({
          path: file.name,
          url: dataUrl,
          orig_name: file.name,
          mime_type: file.type,
          size: file.size,
          meta: { _type: 'gradio.FileData' },
          base64: dataUrl,
        });

        setMultiImagePreviews((prev) => {
          const next = { ...prev, [key]: [...newPreviews] };
          taskManager.saveSpaceState(space.id, { multiImagePreviews: next });
          return next;
        });
        setFormValues((prev) => {
          const next = { ...prev, [key]: [...dataObjects] };
          taskManager.saveSpaceState(space.id, { formValues: next });
          return next;
        });
      };
      reader.readAsDataURL(file);
    });
  };

  const removeMultiImageItem = (key: string, index: number) => {
    setMultiImagePreviews((prev) => {
      const arr = prev[key] ? [...prev[key]] : [];
      arr.splice(index, 1);
      const next = { ...prev, [key]: arr };
      taskManager.saveSpaceState(space.id, { multiImagePreviews: next });
      return next;
    });
    setFormValues((prev) => {
      const arr = prev[key] && Array.isArray(prev[key]) ? [...prev[key]] : [];
      arr.splice(index, 1);
      const next = { ...prev, [key]: arr };
      taskManager.saveSpaceState(space.id, { formValues: next });
      return next;
    });
  };

  // Run the space in background through taskManager
  const handleRun = async () => {
    setIsRunning(true);
    setRunError(null);
    setOutputResult(null);

    try {
      const currentEp = endpoints.find((e) => e.name === selectedEndpoint);
      let payloadData: any[] = [];

      if (currentEp && currentEp.parameters.length > 0) {
        payloadData = currentEp.parameters.map((param, idx) => {
          const key = `param_${idx}`;
          const val = formValues[key];
          if (val === undefined) return null;
          return val;
        });
      } else {
        payloadData = Object.values(formValues);
      }

      await taskManager.startTask(
        space,
        selectedEndpoint || '/predict',
        formValues,
        imagePreviews,
        multiImagePreviews,
        payloadData,
        activeToken,
        subdomain
      );
    } catch (err: any) {
      console.error('Run space error:', err);
      setRunError(err.message || 'Space execution failed. Check console for details.');
      setIsRunning(false);
    }
  };

  const handleStopRun = () => {
    const activeTask = taskManager.getTaskForSpace(space.id);
    if (activeTask) {
      taskManager.cancelTask(activeTask.id);
      setIsRunning(false);
    }
  };

  const handleCopyOutput = () => {
    const text = typeof outputResult === 'string' ? outputResult : JSON.stringify(outputResult, null, 2);
    navigator.clipboard.writeText(text);
    setCopiedOutput(true);
    setTimeout(() => setCopiedOutput(false), 2000);
  };

  const currentEp = endpoints.find((e) => e.name === selectedEndpoint);

  return (
    <div className="flex-1 flex flex-col h-full bg-slate-50 dark:bg-slate-950 overflow-hidden">
      {/* Top Runner App Bar */}
      <header className="px-4 py-3 bg-white dark:bg-slate-900 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between shrink-0 shadow-xs z-10">
        <div className="flex items-center gap-2.5 min-w-0 flex-1 mr-3">
          <button
            onClick={onBack}
            className="p-1.5 -ml-1 rounded-lg text-slate-500 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors cursor-pointer shrink-0"
            aria-label="Back"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>

          <div className="w-8 h-8 rounded-xl bg-slate-100 dark:bg-slate-800 flex items-center justify-center text-base shrink-0 border border-slate-200 dark:border-slate-700">
            <span>{emoji}</span>
          </div>

          <div className="min-w-0 flex-1">
            <h2 className="text-sm font-semibold text-slate-900 dark:text-white truncate">
              {spaceTitle}
            </h2>
            <p className="text-[11px] text-slate-500 dark:text-slate-400 font-mono truncate">
              @{authorName}
            </p>
          </div>
        </div>

        {/* Right Actions: API Key selector */}
        <div className="flex items-center gap-2 shrink-0">
          {apiKeys.length > 0 ? (
            <div className="flex items-center gap-1.5 bg-slate-100 dark:bg-slate-800/80 px-2.5 py-1 rounded-xl border border-slate-200 dark:border-slate-700 text-xs">
              <KeyRound className={`w-3.5 h-3.5 ${activeToken ? 'text-amber-500' : 'text-slate-400'}`} />
              <select
                value={selectedKeyId}
                onChange={(e) => {
                  if (e.target.value === 'manage_keys') {
                    onOpenApiKeys();
                  } else {
                    setSelectedKeyId(e.target.value);
                  }
                }}
                className="bg-transparent text-xs text-slate-800 dark:text-slate-200 focus:outline-hidden cursor-pointer font-medium max-w-[100px] truncate"
              >
                <option value="none" className="dark:bg-slate-900">
                  Public
                </option>
                {apiKeys.map((k) => (
                  <option key={k.id} value={k.id} className="dark:bg-slate-900">
                    {k.name}
                  </option>
                ))}
                <option value="manage_keys" className="text-amber-600 dark:bg-slate-900">
                  + Manage...
                </option>
              </select>
            </div>
          ) : (
            <button
              onClick={onOpenApiKeys}
              className="p-2 rounded-xl text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors cursor-pointer"
              title="Configure API Keys (optional)"
              aria-label="Configure API Keys"
            >
              <KeyRound className="w-4 h-4" />
            </button>
          )}
        </div>
      </header>

      {/* Sleeping alert banner */}
      {isSleeping && (
        <div className="px-4 py-2 bg-amber-500/10 border-b border-amber-500/20 text-[11px] text-amber-800 dark:text-amber-300 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <AlertCircle className="w-3.5 h-3.5 text-amber-500 shrink-0" />
            <span>
              This space is currently <strong>SLEEPING</strong>. Initial inference will wake it up and may take 20&ndash;40s.
            </span>
          </div>
        </div>
      )}

      {/* Main Runner Body: Left Form, Right Output */}
      <div className="flex-1 overflow-y-auto p-4 max-w-5xl mx-auto w-full">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">
          {/* Left Column: Dynamic Inputs Form (7 cols) */}
          <div className="lg:col-span-7 space-y-4">
            {/* Endpoints switcher if space has multiple named endpoints */}
            {endpoints.length > 1 && (
              <div className="p-3 rounded-2xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 shadow-xs flex items-center justify-between gap-3">
                <span className="text-xs font-medium text-slate-700 dark:text-slate-300">
                  Endpoint:
                </span>
                <div className="flex items-center gap-1.5 overflow-x-auto py-0.5">
                  {endpoints.map((ep) => (
                    <button
                      key={ep.name}
                      onClick={() => handleEndpointChange(ep.name)}
                      className={`px-3 py-1 rounded-xl text-xs font-mono font-medium transition-all cursor-pointer ${
                        selectedEndpoint === ep.name
                          ? 'bg-amber-500/20 text-amber-900 dark:text-amber-300 border border-amber-500/40 font-semibold'
                          : 'bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
                      }`}
                    >
                      {ep.name}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {/* Inputs Container */}
            <div className="p-4.5 rounded-2xl bg-white dark:bg-slate-900 border border-slate-200/90 dark:border-slate-800 shadow-xs space-y-4">
              <div className="flex items-center justify-between border-b border-slate-100 dark:border-slate-800 pb-2.5">
                <div className="flex items-center gap-2">
                  <Sliders className="w-4 h-4 text-amber-500" />
                  <h3 className="text-xs font-semibold text-slate-900 dark:text-white">
                    Runtime Parameters
                  </h3>
                </div>

                <button
                  onClick={() => {
                    if (currentEp) initFormForEndpoint(currentEp);
                    else initFallbackForm();
                  }}
                  className="text-[11px] text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 flex items-center gap-1 cursor-pointer"
                >
                  <RotateCcw className="w-3 h-3" />
                  <span>Reset</span>
                </button>
              </div>

              {isLoadingSpec ? (
                <div className="py-12 text-center space-y-2">
                  <Loader2 className="w-6 h-6 animate-spin text-amber-500 mx-auto" />
                  <p className="text-xs text-slate-500 dark:text-slate-400">
                    Generating runtime fields from Space OpenAPI schema...
                  </p>
                </div>
              ) : currentEp && currentEp.parameters.length > 0 ? (
                <div className="space-y-3.5">
                  {currentEp.parameters.map((param, idx) => {
                    const key = `param_${idx}`;
                    const label = param.label || param.parameter_name || `Parameter ${idx + 1}`;
                    const comp = (param.component || '').toLowerCase();
                    const pType = (param.python_type?.type || '').toLowerCase();
                    const val = formValues[key] ?? '';

                    const isImageParam =
                      comp.includes('image') ||
                      pType.includes('filepath') ||
                      param.parameter_name.toLowerCase().includes('image') ||
                      param.parameter_name.toLowerCase().includes('img') ||
                      param.parameter_name.toLowerCase().includes('photo');

                    const isMultipleImages =
                      comp.includes('gallery') ||
                      param.parameter_name.toLowerCase().includes('images') ||
                      param.parameter_name.toLowerCase().includes('files');

                    const isNumberParam =
                      comp.includes('slider') ||
                      pType.includes('int') ||
                      pType.includes('float') ||
                      typeof param.parameter_default === 'number';

                    const isBoolParam = comp.includes('checkbox') || pType.includes('bool');

                    // 1. Multiple Images Input
                    if (isMultipleImages) {
                      return (
                        <div key={key} className="space-y-1.5">
                          <label className="block text-xs font-medium text-slate-700 dark:text-slate-300">
                            {label}
                          </label>
                          <div className="space-y-2">
                            <label className="flex flex-col items-center justify-center p-4 rounded-xl border-2 border-dashed border-slate-200 dark:border-slate-700 hover:border-amber-500 dark:hover:border-amber-500/80 bg-slate-50 dark:bg-slate-800/50 cursor-pointer transition-colors">
                              <Images className="w-6 h-6 text-slate-400 mb-1" />
                              <span className="text-xs text-slate-600 dark:text-slate-300 font-medium">
                                Upload multiple images
                              </span>
                              <span className="text-[10px] text-slate-400">PNG, JPG, WebP</span>
                              <input
                                type="file"
                                accept="image/*"
                                multiple
                                onChange={(e) => {
                                  if (e.target.files) handleMultiImageUpload(key, e.target.files);
                                }}
                                className="hidden"
                              />
                            </label>

                            {multiImagePreviews[key] && multiImagePreviews[key].length > 0 && (
                              <div className="grid grid-cols-4 gap-2">
                                {multiImagePreviews[key].map((src, imgIdx) => (
                                  <div key={imgIdx} className="relative rounded-lg overflow-hidden group aspect-square border border-slate-200 dark:border-slate-700">
                                    <img src={src} alt="Uploaded" className="w-full h-full object-cover" />
                                    <button
                                      onClick={() => removeMultiImageItem(key, imgIdx)}
                                      className="absolute top-1 right-1 p-1 rounded-full bg-black/70 text-white hover:bg-rose-600 transition-colors"
                                    >
                                      <X className="w-3 h-3" />
                                    </button>
                                  </div>
                                ))}
                              </div>
                            )}
                          </div>
                        </div>
                      );
                    }

                    // 2. Single Image Input
                    if (isImageParam) {
                      const preview = imagePreviews[key];
                      return (
                        <div key={key} className="space-y-1.5">
                          <label className="block text-xs font-medium text-slate-700 dark:text-slate-300">
                            {label}
                          </label>
                          {preview ? (
                            <div className="relative rounded-xl overflow-hidden border border-slate-200 dark:border-slate-700 max-h-48 bg-slate-900 flex items-center justify-center group">
                              <img src={preview} alt="Uploaded" className="max-h-48 object-contain" />
                              <button
                                onClick={() => removeSingleImage(key)}
                                className="absolute top-2 right-2 p-1.5 rounded-lg bg-black/70 text-white hover:bg-rose-600 transition-colors cursor-pointer"
                                title="Remove image"
                              >
                                <X className="w-3.5 h-3.5" />
                              </button>
                            </div>
                          ) : (
                            <label className="flex flex-col items-center justify-center p-4 rounded-xl border-2 border-dashed border-slate-200 dark:border-slate-700 hover:border-amber-500 dark:hover:border-amber-500/80 bg-slate-50 dark:bg-slate-800/50 cursor-pointer transition-colors">
                              <Upload className="w-5 h-5 text-slate-400 mb-1" />
                              <span className="text-xs text-slate-600 dark:text-slate-300 font-medium">
                                Click or drag image here
                              </span>
                              <span className="text-[10px] text-slate-400">PNG, JPG, WebP</span>
                              <input
                                type="file"
                                accept="image/*"
                                onChange={(e) => {
                                  if (e.target.files && e.target.files[0]) {
                                    handleSingleImageUpload(key, e.target.files[0]);
                                  }
                                }}
                                className="hidden"
                              />
                            </label>
                          )}
                        </div>
                      );
                    }

                    // 3. Boolean / Switch Input
                    if (isBoolParam) {
                      return (
                        <div
                          key={key}
                          className="flex items-center justify-between p-2.5 rounded-xl bg-slate-50 dark:bg-slate-800/60 border border-slate-200/80 dark:border-slate-800"
                        >
                          <span className="text-xs font-medium text-slate-700 dark:text-slate-300">
                            {label}
                          </span>
                          <input
                            type="checkbox"
                            checked={!!val}
                            onChange={(e) => updateFormValue(key, e.target.checked)}
                            className="w-4 h-4 rounded text-amber-500 focus:ring-amber-500 border-slate-300 dark:border-slate-600 cursor-pointer"
                          />
                        </div>
                      );
                    }

                    // 4. Number / Slider Input
                    if (isNumberParam) {
                      const numVal = typeof val === 'number' ? val : 0;
                      return (
                        <div key={key} className="space-y-1">
                          <div className="flex items-center justify-between">
                            <label className="text-xs font-medium text-slate-700 dark:text-slate-300">
                              {label}
                            </label>
                            <span className="text-xs font-mono text-amber-600 dark:text-amber-400 font-semibold">
                              {numVal}
                            </span>
                          </div>
                          <div className="flex items-center gap-3">
                            <input
                              type="range"
                              min={0}
                              max={100}
                              step={1}
                              value={numVal}
                              onChange={(e) => updateFormValue(key, parseFloat(e.target.value))}
                              className="flex-1 h-1.5 bg-slate-200 dark:bg-slate-700 rounded-lg appearance-none cursor-pointer accent-amber-500"
                            />
                            <input
                              type="number"
                              value={numVal}
                              onChange={(e) => updateFormValue(key, parseFloat(e.target.value) || 0)}
                              className="w-16 px-2 py-1 text-xs font-mono rounded-lg border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 text-slate-900 dark:text-slate-100 text-right"
                            />
                          </div>
                        </div>
                      );
                    }

                    // 5. Text / Textarea Input
                    const isLongText =
                      label.toLowerCase().includes('prompt') ||
                      label.toLowerCase().includes('text') ||
                      label.toLowerCase().includes('query');

                    return (
                      <div key={key} className="space-y-1">
                        <label className="block text-xs font-medium text-slate-700 dark:text-slate-300">
                          {label}
                        </label>
                        {isLongText ? (
                          <textarea
                            rows={3}
                            placeholder={param.example_input ? String(param.example_input) : `Enter ${label}...`}
                            value={val}
                            onChange={(e) => updateFormValue(key, e.target.value)}
                            className="w-full px-3 py-2 text-xs rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 text-slate-900 dark:text-slate-100 placeholder-slate-400 focus:outline-hidden focus:border-amber-500 transition-colors"
                          />
                        ) : (
                          <input
                            type="text"
                            placeholder={param.example_input ? String(param.example_input) : `Enter ${label}...`}
                            value={val}
                            onChange={(e) => updateFormValue(key, e.target.value)}
                            className="w-full px-3 py-2 text-xs rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 text-slate-900 dark:text-slate-100 placeholder-slate-400 focus:outline-hidden focus:border-amber-500 transition-colors"
                          />
                        )}
                      </div>
                    );
                  })}
                </div>
              ) : (
                /* Fallback inputs if spec wasn't introspected */
                <div className="space-y-3">
                  <div>
                    <label className="block text-xs font-medium text-slate-700 dark:text-slate-300 mb-1">
                      Prompt
                    </label>
                    <textarea
                      rows={3}
                      value={formValues.param_0 || ''}
                      onChange={(e) => updateFormValue('param_0', e.target.value)}
                      placeholder="Describe what you want to generate..."
                      className="w-full px-3 py-2 text-xs rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 text-slate-900 dark:text-slate-100 focus:outline-hidden focus:border-amber-500"
                    />
                  </div>

                  <div>
                    <label className="block text-xs font-medium text-slate-700 dark:text-slate-300 mb-1">
                      Optional Image Input
                    </label>
                    {imagePreviews.param_1 ? (
                      <div className="relative rounded-xl overflow-hidden border border-slate-200 dark:border-slate-700 max-h-40 bg-slate-900 flex items-center justify-center">
                        <img
                          src={imagePreviews.param_1}
                          alt="Input"
                          className="max-h-40 object-contain"
                        />
                        <button
                          onClick={() => removeSingleImage('param_1')}
                          className="absolute top-2 right-2 p-1.5 rounded-lg bg-black/70 text-white"
                        >
                          <X className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    ) : (
                      <label className="flex flex-col items-center justify-center p-3 rounded-xl border-2 border-dashed border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800/40 cursor-pointer">
                        <Upload className="w-4 h-4 text-slate-400 mb-1" />
                        <span className="text-xs text-slate-500 font-medium">Upload image</span>
                        <input
                          type="file"
                          accept="image/*"
                          onChange={(e) => {
                            if (e.target.files && e.target.files[0]) {
                              handleSingleImageUpload('param_1', e.target.files[0]);
                            }
                          }}
                          className="hidden"
                        />
                      </label>
                    )}
                  </div>
                </div>
              )}

              {/* Submit / Run Button & Stop Controls */}
              <div className="pt-2 space-y-2">
                {isRunning ? (
                  <div className="flex items-center gap-2">
                    <div className="flex-1 py-2.5 px-4 rounded-xl bg-amber-500/15 border border-amber-500/30 text-amber-900 dark:text-amber-300 font-semibold text-xs flex items-center justify-center gap-2">
                      <Loader2 className="w-4 h-4 animate-spin text-amber-600 dark:text-amber-400" />
                      <span>Generating ({executionTime}s)...</span>
                    </div>

                    <button
                      id="btn-stop-space"
                      onClick={handleStopRun}
                      className="py-2.5 px-4 rounded-xl bg-rose-500 hover:bg-rose-600 text-white font-semibold text-xs transition-colors flex items-center gap-1.5 cursor-pointer shadow-xs"
                      title="Cancel background execution"
                    >
                      <StopCircle className="w-4 h-4" />
                      <span>Stop</span>
                    </button>
                  </div>
                ) : (
                  <button
                    id="btn-run-space"
                    onClick={handleRun}
                    className="w-full py-2.5 px-4 rounded-xl bg-amber-500 hover:bg-amber-600 active:scale-98 text-slate-950 font-semibold text-xs transition-all flex items-center justify-center gap-2 cursor-pointer shadow-xs"
                  >
                    <Sparkles className="w-4 h-4 text-slate-950" />
                    <span>Run Space / Generate</span>
                  </button>
                )}

                {isRunning && (
                  <p className="text-[10px] text-center text-slate-400">
                    Running in background &bull; Safe to navigate to other spaces or media
                  </p>
                )}
              </div>

              {runError && (
                <div className="p-3 rounded-xl bg-rose-50 dark:bg-rose-950/30 border border-rose-200 dark:border-rose-900/50 text-xs text-rose-600 dark:text-rose-400 flex items-start gap-2">
                  <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
                  <div className="min-w-0 flex-1">
                    <p className="font-semibold">Inference Error</p>
                    <p className="text-[11px] leading-relaxed mt-0.5 whitespace-pre-wrap">{runError}</p>
                  </div>
                </div>
              )}
            </div>
          </div>

          {/* Right Column: Live Output Field (5 cols) */}
          <div className="lg:col-span-5 space-y-4">
            <div className="p-4 rounded-2xl bg-white dark:bg-slate-900 border border-slate-200/90 dark:border-slate-800 shadow-xs min-h-[380px] flex flex-col">
              <div className="flex items-center justify-between border-b border-slate-100 dark:border-slate-800 pb-2.5 shrink-0">
                <div className="flex items-center gap-2">
                  <Sparkles className="w-4 h-4 text-amber-500" />
                  <h3 className="text-xs font-semibold text-slate-900 dark:text-white">
                    Output Results
                  </h3>
                </div>

                {outputResult && (
                  <button
                    onClick={handleCopyOutput}
                    className="text-[11px] text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 flex items-center gap-1 cursor-pointer"
                  >
                    {copiedOutput ? (
                      <>
                        <Check className="w-3 h-3 text-emerald-500" />
                        <span className="text-emerald-500">Copied</span>
                      </>
                    ) : (
                      <>
                        <Copy className="w-3 h-3" />
                        <span>Copy</span>
                      </>
                    )}
                  </button>
                )}
              </div>

              {/* Output Content Display */}
              <div className="flex-1 flex items-center justify-center p-2">
                {isRunning ? (
                  <div className="py-12 text-center space-y-3">
                    <div className="relative w-12 h-12 mx-auto">
                      <div className="w-12 h-12 rounded-full border-2 border-amber-500/20 border-t-amber-500 animate-spin" />
                      <Sparkles className="w-5 h-5 text-amber-500 absolute inset-0 m-auto animate-pulse" />
                    </div>
                    <div>
                      <p className="text-xs font-medium text-slate-800 dark:text-slate-200">
                        Generating outputs...
                      </p>
                      <p className="text-[11px] font-mono text-amber-600 dark:text-amber-400 mt-0.5">
                        Elapsed: {executionTime}s
                      </p>
                    </div>
                  </div>
                ) : outputResult ? (
                  <div className="w-full h-full flex flex-col justify-center space-y-3 animate-in fade-in duration-200">
                    <OutputRenderer data={outputResult} subdomain={subdomain} />
                  </div>
                ) : (
                  <div className="py-16 text-center space-y-2">
                    <div className="w-10 h-10 rounded-2xl bg-slate-100 dark:bg-slate-800 flex items-center justify-center mx-auto text-slate-400">
                      <Sparkles className="w-5 h-5" />
                    </div>
                    <p className="text-xs text-slate-400 dark:text-slate-500">
                      Output from the space will appear here
                    </p>
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

// Component that dynamically handles image, video, audio, text, or json responses
function OutputRenderer({ data, subdomain }: { data: any; subdomain: string }) {
  // Helper to resolve URL
  const resolveUrl = (raw: string) => {
    if (!raw) return '';
    if (raw.startsWith('http://') || raw.startsWith('https://')) return raw;
    if (raw.startsWith('/')) return `https://${subdomain}.hf.space${raw}`;
    return `https://${subdomain}.hf.space/gradio_api/file=${raw}`;
  };

  // 1. Single Image or FileData object
  if (data && typeof data === 'object' && (data.url || data.path)) {
    const fileUrl = resolveUrl(data.url || data.path);
    const mime = data.mime_type || '';

    if (mime.includes('video') || fileUrl.match(/\.(mp4|webm|mov)$/i)) {
      return (
        <div className="space-y-2">
          <video src={fileUrl} controls autoPlay loop className="w-full rounded-xl max-h-80 bg-black" />
          <div className="flex justify-end">
            <a
              href={fileUrl}
              download
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-slate-100 dark:bg-slate-800 text-xs font-medium hover:bg-slate-200 transition-colors"
            >
              <Download className="w-3.5 h-3.5" />
              <span>Download Video</span>
            </a>
          </div>
        </div>
      );
    }

    return (
      <div className="space-y-2">
        <div className="rounded-xl overflow-hidden border border-slate-200 dark:border-slate-700 bg-slate-950 flex items-center justify-center">
          <img src={fileUrl} alt="Output" className="max-h-96 w-full object-contain" />
        </div>
        <div className="flex justify-end">
          <a
            href={fileUrl}
            download="output.png"
            target="_blank"
            rel="noreferrer"
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-slate-100 dark:bg-slate-800 text-xs font-medium hover:bg-slate-200 transition-colors"
          >
            <Download className="w-3.5 h-3.5" />
            <span>Download Image</span>
          </a>
        </div>
      </div>
    );
  }

  // 2. Array of items (e.g. tuple of outputs or gallery of images)
  if (Array.isArray(data)) {
    return (
      <div className="space-y-3 w-full">
        {data.map((item, idx) => (
          <div key={idx} className="w-full">
            <OutputRenderer data={item} subdomain={subdomain} />
          </div>
        ))}
      </div>
    );
  }

  // 3. String URL check
  if (typeof data === 'string') {
    const isImage = data.match(/\.(png|jpg|jpeg|webp|gif)$/i) || data.startsWith('data:image/');
    const isVideo = data.match(/\.(mp4|webm|mov)$/i);
    const isAudio = data.match(/\.(mp3|wav|ogg)$/i);

    if (isImage) {
      const url = resolveUrl(data);
      return (
        <div className="space-y-2">
          <div className="rounded-xl overflow-hidden border border-slate-200 dark:border-slate-700 bg-slate-950 flex items-center justify-center">
            <img src={url} alt="Result" className="max-h-96 object-contain" />
          </div>
          <div className="flex justify-end">
            <a
              href={url}
              download
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-slate-100 dark:bg-slate-800 text-xs font-medium hover:bg-slate-200 transition-colors"
            >
              <Download className="w-3.5 h-3.5" />
              <span>Download</span>
            </a>
          </div>
        </div>
      );
    }

    if (isVideo) {
      const url = resolveUrl(data);
      return (
        <video src={url} controls autoPlay loop className="w-full rounded-xl max-h-80 bg-black" />
      );
    }

    if (isAudio) {
      const url = resolveUrl(data);
      return (
        <div className="p-4 rounded-xl bg-slate-100 dark:bg-slate-800">
          <audio src={url} controls className="w-full" />
        </div>
      );
    }

    // Text / markdown output
    return (
      <div className="p-3.5 rounded-xl bg-slate-50 dark:bg-slate-800/80 border border-slate-200 dark:border-slate-700 text-xs font-mono text-slate-800 dark:text-slate-100 leading-relaxed whitespace-pre-wrap max-h-96 overflow-y-auto w-full">
        {data}
      </div>
    );
  }

  // 4. JSON / Object fallback
  return (
    <pre className="p-3 rounded-xl bg-slate-950 text-slate-200 font-mono text-[11px] max-h-80 overflow-auto border border-slate-800 w-full">
      {JSON.stringify(data, null, 2)}
    </pre>
  );
}

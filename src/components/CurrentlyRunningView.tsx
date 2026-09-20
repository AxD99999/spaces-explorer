import React, { useState, useEffect } from 'react';
import {
  Activity,
  ArrowLeft,
  Loader2,
  StopCircle,
  ExternalLink,
  Sparkles,
  Compass,
  CheckCircle2,
  AlertCircle,
  Clock,
  ChevronRight,
  Trash2,
} from 'lucide-react';
import { ActiveSpaceTask, HfSpace } from '../types';
import { taskManager } from '../services/taskManager';

interface CurrentlyRunningViewProps {
  onOpenSpace: (space: HfSpace) => void;
  onBackToBrowse: () => void;
  onOpenMedia?: () => void;
}

export const CurrentlyRunningView: React.FC<CurrentlyRunningViewProps> = ({
  onOpenSpace,
  onBackToBrowse,
  onOpenMedia,
}) => {
  const [tasks, setTasks] = useState<ActiveSpaceTask[]>(() => taskManager.getAllTasks());

  useEffect(() => {
    const unsubscribe = taskManager.subscribe((updatedTasks) => {
      setTasks([...updatedTasks]);
    });
    return unsubscribe;
  }, []);

  const runningTasks = tasks.filter((t) => t.status === 'running');
  const finishedTasks = tasks.filter((t) => t.status !== 'running');

  const getPromptPreview = (formValues: Record<string, any>) => {
    for (const val of Object.values(formValues)) {
      if (typeof val === 'string' && val.trim().length > 3) {
        return val.trim();
      }
    }
    return 'Inference task in progress';
  };

  return (
    <div className="flex-1 flex flex-col h-full bg-slate-50 dark:bg-slate-950 overflow-hidden">
      {/* Header */}
      <header className="px-4 py-3 bg-white dark:bg-slate-900 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between shrink-0 shadow-xs">
        <div className="flex items-center gap-2.5">
          <button
            onClick={onBackToBrowse}
            className="p-1.5 -ml-1 rounded-lg text-slate-500 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors cursor-pointer"
            aria-label="Back to browse"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <div>
            <h2 className="text-sm font-semibold text-slate-900 dark:text-white flex items-center gap-2">
              <Activity className="w-4 h-4 text-emerald-500" />
              Currently Running
              {runningTasks.length > 0 && (
                <span className="px-1.5 py-0.2 rounded-full text-[10px] font-mono font-semibold bg-emerald-500/15 text-emerald-700 dark:text-emerald-400 border border-emerald-500/30 animate-pulse">
                  {runningTasks.length} active
                </span>
              )}
            </h2>
            <p className="text-[11px] text-slate-500 dark:text-slate-400">
              Background inference & space execution
            </p>
          </div>
        </div>

        {finishedTasks.length > 0 && (
          <button
            onClick={() => taskManager.clearCompletedTasks()}
            className="flex items-center gap-1 px-2.5 py-1 text-[11px] text-slate-500 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg transition-colors cursor-pointer"
          >
            <Trash2 className="w-3 h-3" />
            <span>Clear History</span>
          </button>
        )}
      </header>

      {/* Main Task List */}
      <div className="flex-1 overflow-y-auto p-4 space-y-4 max-w-3xl mx-auto w-full">
        {/* Section 1: Running Tasks */}
        <div className="space-y-2.5">
          <div className="flex items-center justify-between">
            <h3 className="text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider flex items-center gap-1.5">
              <span className="w-2 h-2 rounded-full bg-emerald-500 animate-ping" />
              <span>Active Background Runs ({runningTasks.length})</span>
            </h3>
          </div>

          {runningTasks.length === 0 ? (
            <div className="p-8 rounded-2xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 text-center space-y-2">
              <div className="w-10 h-10 rounded-xl bg-slate-100 dark:bg-slate-800 text-slate-400 flex items-center justify-center mx-auto">
                <Activity className="w-5 h-5" />
              </div>
              <p className="text-xs font-medium text-slate-700 dark:text-slate-300">
                No Spaces Currently Running
              </p>
              <p className="text-[11px] text-slate-500 dark:text-slate-400 max-w-xs mx-auto leading-relaxed">
                When you run a space, it will keep generating in the background even if you leave the screen.
              </p>
              <div className="pt-2">
                <button
                  onClick={onBackToBrowse}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-slate-900 dark:bg-white text-white dark:text-slate-950 text-xs font-semibold hover:opacity-90 transition-opacity cursor-pointer shadow-xs"
                >
                  <Compass className="w-3.5 h-3.5" />
                  <span>Browse Spaces</span>
                </button>
              </div>
            </div>
          ) : (
            <div className="space-y-2.5">
              {runningTasks.map((task) => {
                const prompt = getPromptPreview(task.formValues);
                const spaceTitle = task.space.title || task.space.id.split('/')[1] || task.space.id;
                const author = task.space.author || task.space.id.split('/')[0] || 'community';
                const emoji = task.space.emoji || '🤗';

                return (
                  <div
                    key={task.id}
                    className="p-3.5 rounded-2xl bg-white dark:bg-slate-900 border border-emerald-500/30 dark:border-emerald-500/25 shadow-xs flex flex-col gap-2.5 hover:border-emerald-500/50 transition-colors"
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div className="flex items-start gap-2.5 min-w-0 flex-1">
                        <div className="w-10 h-10 rounded-xl bg-emerald-500/10 dark:bg-emerald-500/15 border border-emerald-500/20 flex items-center justify-center text-xl shrink-0">
                          <span>{emoji}</span>
                        </div>

                        <div className="min-w-0 flex-1">
                          <div className="flex items-center gap-2">
                            <h4 className="text-xs font-semibold text-slate-900 dark:text-white truncate">
                              {spaceTitle}
                            </h4>
                            <span className="text-[10px] font-mono text-emerald-600 dark:text-emerald-400 font-semibold px-1.5 py-0.2 rounded bg-emerald-500/10 border border-emerald-500/20 flex items-center gap-1">
                              <Loader2 className="w-2.5 h-2.5 animate-spin" />
                              <span>{task.elapsedSeconds}s</span>
                            </span>
                          </div>
                          <p className="text-[11px] text-slate-500 dark:text-slate-400 font-mono">
                            @{author} &bull; {task.endpoint}
                          </p>
                        </div>
                      </div>

                      {/* Cancel / Stop button */}
                      <button
                        onClick={() => taskManager.cancelTask(task.id)}
                        className="p-1.5 rounded-lg text-slate-400 hover:text-rose-600 hover:bg-rose-50 dark:hover:bg-rose-950/30 transition-colors cursor-pointer"
                        title="Cancel run"
                      >
                        <StopCircle className="w-4 h-4" />
                      </button>
                    </div>

                    {/* Prompt summary */}
                    <div className="px-2.5 py-1.5 rounded-xl bg-slate-50 dark:bg-slate-800/60 border border-slate-100 dark:border-slate-800 text-[11px] text-slate-600 dark:text-slate-300 font-mono truncate">
                      {prompt}
                    </div>

                    {/* Action Bar */}
                    <div className="flex items-center justify-between pt-1 border-t border-slate-100 dark:border-slate-800">
                      <span className="text-[10px] text-slate-400 flex items-center gap-1">
                        <Clock className="w-3 h-3" /> Running in background
                      </span>

                      <button
                        onClick={() => onOpenSpace(task.space)}
                        className="flex items-center gap-1 px-3 py-1 rounded-xl bg-emerald-500 hover:bg-emerald-600 text-slate-950 text-xs font-semibold transition-colors cursor-pointer shadow-2xs"
                      >
                        <span>Open Space</span>
                        <ChevronRight className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Section 2: Recent Session History */}
        {finishedTasks.length > 0 && (
          <div className="space-y-2.5 pt-2">
            <h3 className="text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider">
              Recent Session Runs ({finishedTasks.length})
            </h3>

            <div className="space-y-2">
              {finishedTasks.map((task) => {
                const isSuccess = task.status === 'completed';
                const spaceTitle = task.space.title || task.space.id.split('/')[1] || task.space.id;
                const emoji = task.space.emoji || '🤗';

                return (
                  <div
                    key={task.id}
                    className="p-3 rounded-xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 shadow-2xs flex items-center justify-between gap-3"
                  >
                    <div className="flex items-center gap-2.5 min-w-0 flex-1">
                      <div className="w-8 h-8 rounded-lg bg-slate-100 dark:bg-slate-800 flex items-center justify-center text-sm shrink-0">
                        <span>{emoji}</span>
                      </div>
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-2">
                          <span className="text-xs font-semibold text-slate-900 dark:text-white truncate">
                            {spaceTitle}
                          </span>
                          {isSuccess ? (
                            <span className="text-[10px] font-mono text-emerald-600 dark:text-emerald-400 flex items-center gap-0.5">
                              <CheckCircle2 className="w-3 h-3" /> {task.elapsedSeconds}s
                            </span>
                          ) : (
                            <span className="text-[10px] font-mono text-rose-500 flex items-center gap-0.5">
                              <AlertCircle className="w-3 h-3" /> failed
                            </span>
                          )}
                        </div>
                        <p className="text-[10px] text-slate-400 truncate">
                          {task.error || (isSuccess ? 'Output generated and saved to Media' : '')}
                        </p>
                      </div>
                    </div>

                    <div className="flex items-center gap-1.5 shrink-0">
                      {isSuccess && onOpenMedia && (
                        <button
                          onClick={onOpenMedia}
                          className="px-2.5 py-1 rounded-lg text-xs font-medium text-amber-600 dark:text-amber-400 hover:bg-amber-500/10 transition-colors cursor-pointer"
                        >
                          View Media
                        </button>
                      )}
                      <button
                        onClick={() => onOpenSpace(task.space)}
                        className="px-2.5 py-1 rounded-lg text-xs font-semibold bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700 transition-colors cursor-pointer"
                      >
                        Open
                      </button>
                      <button
                        onClick={() => taskManager.removeTask(task.id)}
                        className="p-1.5 text-slate-400 hover:text-rose-500 rounded-lg transition-colors cursor-pointer"
                        title="Remove"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

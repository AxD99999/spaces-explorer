import React from 'react';
import { RefreshCw, Database, Sparkles, HardDriveDownload, Rocket, SlidersHorizontal } from 'lucide-react';
import { motion } from 'motion/react';

interface EmptyStateProps {
  onRefresh: () => void;
  isLoading: boolean;
}

export const EmptyState: React.FC<EmptyStateProps> = ({ onRefresh, isLoading }) => {
  return (
    <motion.div
      id="empty-state-container"
      initial={{ opacity: 0, scale: 0.96 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ duration: 0.3 }}
      className="flex flex-col items-center justify-center min-h-[60vh] px-6 py-12 text-center"
    >
      <div className="relative mb-6">
        <div className="w-24 h-24 rounded-3xl bg-gradient-to-tr from-amber-500/20 via-indigo-500/20 to-sky-500/20 flex items-center justify-center border border-slate-700/50 shadow-inner">
          <span className="text-4xl select-none">🚀</span>
        </div>
        <div className="absolute -bottom-2 -right-2 p-2 rounded-2xl bg-indigo-600 text-white shadow-lg border-2 border-slate-900">
          <Database className="w-4 h-4" />
        </div>
      </div>

      <h2 className="text-xl font-semibold text-slate-100 mb-2">
        No Spaces Loaded
      </h2>
      <p className="text-sm text-slate-400 max-w-sm mb-6 leading-relaxed">
        The spaces list is empty initially. Tap the refresh button to send a request to the Hugging Face Spaces API and display currently active spaces.
      </p>

      {/* Primary Action Button */}
      <button
        id="btn-fetch-spaces-empty"
        onClick={onRefresh}
        disabled={isLoading}
        className="group relative inline-flex items-center gap-3 px-6 py-3.5 rounded-full bg-gradient-to-r from-amber-500 to-amber-600 text-slate-950 font-semibold shadow-lg shadow-amber-500/20 hover:shadow-amber-500/30 hover:brightness-105 active:scale-95 disabled:opacity-60 transition-all cursor-pointer"
      >
        <RefreshCw className={`w-5 h-5 ${isLoading ? 'animate-spin' : 'group-hover:rotate-45 transition-transform'}`} />
        <span>{isLoading ? 'Connecting to spaces-json...' : 'Fetch Active Spaces'}</span>
      </button>

      {/* Feature notice cards */}
      <div className="mt-10 grid grid-cols-1 sm:grid-cols-2 gap-3 w-full max-w-md text-left">
        <div className="p-3.5 rounded-2xl bg-slate-800/40 border border-slate-800 flex items-start gap-3">
          <div className="p-2 rounded-xl bg-amber-500/10 text-amber-400 shrink-0 mt-0.5">
            <HardDriveDownload className="w-4 h-4" />
          </div>
          <div>
            <h4 className="text-xs font-semibold text-slate-200">Device Storage</h4>
            <p className="text-xs text-slate-400 mt-0.5 leading-relaxed">
              When loaded, all spaces and details are saved to device storage and persist when the app is closed.
            </p>
          </div>
        </div>

        <div className="p-3.5 rounded-2xl bg-slate-800/40 border border-slate-800 flex items-start gap-3">
          <div className="p-2 rounded-xl bg-indigo-500/10 text-indigo-400 shrink-0 mt-0.5">
            <SlidersHorizontal className="w-4 h-4" />
          </div>
          <div>
            <h4 className="text-xs font-semibold text-slate-200">Filters & Search</h4>
            <p className="text-xs text-slate-400 mt-0.5 leading-relaxed">
              Filter by SDK (Gradio, Docker, Streamlit), hardware, active status, categories, and creators.
            </p>
          </div>
        </div>
      </div>

      <div className="mt-6 text-[11px] text-slate-400 font-mono">
        Endpoint: huggingface.co/spaces-json
      </div>
    </motion.div>
  );
};

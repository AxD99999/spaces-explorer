import React from 'react';
import { motion, AnimatePresence } from 'motion/react';
import {
  Compass,
  Star,
  KeyRound,
  X,
  ExternalLink,
  Sun,
  Moon,
  ShieldCheck,
  Sparkles,
  Activity,
  Film,
} from 'lucide-react';
import { AppNavView } from '../types';

interface NavigationDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  currentView: AppNavView;
  onSelectView: (view: AppNavView) => void;
  runningCount: number;
  mediaCount: number;
  favouritesCount: number;
  apiKeysCount: number;
  theme: 'light' | 'dark';
  onToggleTheme: () => void;
}

export const NavigationDrawer: React.FC<NavigationDrawerProps> = ({
  isOpen,
  onClose,
  currentView,
  onSelectView,
  runningCount,
  mediaCount,
  favouritesCount,
  apiKeysCount,
  theme,
  onToggleTheme,
}) => {
  return (
    <AnimatePresence>
      {isOpen && (
        <div className="fixed inset-0 z-50 flex">
          {/* Dimmed Backdrop */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.2 }}
            onClick={onClose}
            className="fixed inset-0 bg-black/60 backdrop-blur-xs"
          />

          {/* Drawer Content */}
          <motion.div
            initial={{ x: '-100%' }}
            animate={{ x: 0 }}
            exit={{ x: '-100%' }}
            transition={{ type: 'spring', damping: 28, stiffness: 300 }}
            className="relative w-72 max-w-[82vw] h-full bg-white dark:bg-slate-900 border-r border-slate-200 dark:border-slate-800 shadow-2xl flex flex-col z-10 select-none overflow-hidden"
          >
            {/* Header */}
            <div className="p-4 border-b border-slate-100 dark:border-slate-800 flex items-center justify-between">
              <div className="flex items-center gap-2.5">
                <div className="w-8 h-8 rounded-xl bg-amber-500/15 dark:bg-amber-400/15 border border-amber-500/25 flex items-center justify-center text-base">
                  🤗
                </div>
                <div>
                  <h3 className="text-sm font-semibold text-slate-900 dark:text-white tracking-tight">
                    Hugging Face
                  </h3>
                  <p className="text-[11px] text-slate-500 dark:text-slate-400 font-mono">
                    Spaces Studio
                  </p>
                </div>
              </div>

              <button
                onClick={onClose}
                className="p-1.5 rounded-lg text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors cursor-pointer"
                aria-label="Close menu"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {/* Navigation Options */}
            <div className="flex-1 py-3 px-2.5 space-y-1.5 overflow-y-auto">
              {/* Option 1: Browse spaces */}
              <button
                id="nav-browse-spaces"
                onClick={() => {
                  onSelectView('browse');
                  onClose();
                }}
                className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-medium transition-all cursor-pointer ${
                  currentView === 'browse'
                    ? 'bg-amber-500/15 text-amber-900 dark:text-amber-300 font-semibold border border-amber-500/30 shadow-xs'
                    : 'text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800/80'
                }`}
              >
                <div className="flex items-center gap-3">
                  <Compass
                    className={`w-4 h-4 ${
                      currentView === 'browse'
                        ? 'text-amber-600 dark:text-amber-400'
                        : 'text-slate-500 dark:text-slate-400'
                    }`}
                  />
                  <span>Browse spaces</span>
                </div>
                <span
                  className={`text-[10px] px-1.5 py-0.2 rounded-full ${
                    currentView === 'browse'
                      ? 'bg-amber-500/20 text-amber-800 dark:text-amber-300'
                      : 'text-slate-400'
                  }`}
                >
                  Live
                </span>
              </button>

              {/* Option 2: Currently running */}
              <button
                id="nav-currently-running"
                onClick={() => {
                  onSelectView('currently-running');
                  onClose();
                }}
                className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-medium transition-all cursor-pointer ${
                  currentView === 'currently-running'
                    ? 'bg-amber-500/15 text-amber-900 dark:text-amber-300 font-semibold border border-amber-500/30 shadow-xs'
                    : 'text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800/80'
                }`}
              >
                <div className="flex items-center gap-3">
                  <Activity
                    className={`w-4 h-4 ${
                      runningCount > 0
                        ? 'text-emerald-500 animate-pulse'
                        : currentView === 'currently-running'
                        ? 'text-amber-600 dark:text-amber-400'
                        : 'text-slate-500 dark:text-slate-400'
                    }`}
                  />
                  <span>Currently running</span>
                </div>
                {runningCount > 0 ? (
                  <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-emerald-500/20 text-emerald-700 dark:text-emerald-300 border border-emerald-500/30 flex items-center gap-1">
                    <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-ping" />
                    {runningCount}
                  </span>
                ) : (
                  <span className="text-[10px] text-slate-400 font-mono">0</span>
                )}
              </button>

              {/* Option 3: Media */}
              <button
                id="nav-media"
                onClick={() => {
                  onSelectView('media');
                  onClose();
                }}
                className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-medium transition-all cursor-pointer ${
                  currentView === 'media'
                    ? 'bg-amber-500/15 text-amber-900 dark:text-amber-300 font-semibold border border-amber-500/30 shadow-xs'
                    : 'text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800/80'
                }`}
              >
                <div className="flex items-center gap-3">
                  <Film
                    className={`w-4 h-4 ${
                      currentView === 'media'
                        ? 'text-amber-600 dark:text-amber-400'
                        : 'text-slate-500 dark:text-slate-400'
                    }`}
                  />
                  <span>Media</span>
                </div>
                {mediaCount > 0 ? (
                  <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-amber-500/20 text-amber-800 dark:text-amber-300 border border-amber-500/30">
                    {mediaCount}
                  </span>
                ) : (
                  <span className="text-[10px] text-slate-400 font-mono">0</span>
                )}
              </button>

              {/* Option 4: Favourites */}
              <button
                id="nav-favourites"
                onClick={() => {
                  onSelectView('favourites');
                  onClose();
                }}
                className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-medium transition-all cursor-pointer ${
                  currentView === 'favourites'
                    ? 'bg-amber-500/15 text-amber-900 dark:text-amber-300 font-semibold border border-amber-500/30 shadow-xs'
                    : 'text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800/80'
                }`}
              >
                <div className="flex items-center gap-3">
                  <Star
                    className={`w-4 h-4 ${
                      currentView === 'favourites'
                        ? 'text-amber-500 fill-amber-500'
                        : 'text-slate-500 dark:text-slate-400'
                    }`}
                  />
                  <span>Favourites</span>
                </div>
                {favouritesCount > 0 && (
                  <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-amber-500/20 text-amber-800 dark:text-amber-300 border border-amber-500/30">
                    {favouritesCount}
                  </span>
                )}
              </button>

              {/* Option 5: API keys */}
              <button
                id="nav-api-keys"
                onClick={() => {
                  onSelectView('api-keys');
                  onClose();
                }}
                className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-medium transition-all cursor-pointer ${
                  currentView === 'api-keys'
                    ? 'bg-amber-500/15 text-amber-900 dark:text-amber-300 font-semibold border border-amber-500/30 shadow-xs'
                    : 'text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800/80'
                }`}
              >
                <div className="flex items-center gap-3">
                  <KeyRound
                    className={`w-4 h-4 ${
                      currentView === 'api-keys'
                        ? 'text-amber-600 dark:text-amber-400'
                        : 'text-slate-500 dark:text-slate-400'
                    }`}
                  />
                  <span>API keys</span>
                </div>
                <div className="flex items-center gap-1.5">
                  {apiKeysCount > 0 ? (
                    <span className="text-[10px] font-medium px-2 py-0.5 rounded-full bg-emerald-500/15 text-emerald-700 dark:text-emerald-300 border border-emerald-500/20">
                      {apiKeysCount} {apiKeysCount === 1 ? 'key' : 'keys'}
                    </span>
                  ) : (
                    <span className="text-[10px] text-slate-400 font-normal">Add key</span>
                  )}
                </div>
              </button>
            </div>

            {/* Bottom info & theme toggle */}
            <div className="p-3 border-t border-slate-100 dark:border-slate-800 space-y-2">
              {/* Theme toggle button */}
              <button
                onClick={onToggleTheme}
                className="w-full flex items-center justify-between px-3 py-2 rounded-xl text-xs font-medium text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800/80 transition-colors cursor-pointer"
              >
                <div className="flex items-center gap-2.5">
                  {theme === 'dark' ? (
                    <Moon className="w-4 h-4 text-amber-400" />
                  ) : (
                    <Sun className="w-4 h-4 text-amber-600" />
                  )}
                  <span>{theme === 'dark' ? 'Dark theme' : 'Light theme'}</span>
                </div>
                <span className="text-[10px] font-mono text-slate-400 uppercase">
                  {theme}
                </span>
              </button>

              {/* Hugging Face Spaces as API documentation link */}
              <a
                href="https://huggingface.co/docs/hub/en/spaces-api-endpoints"
                target="_blank"
                rel="noreferrer"
                className="w-full flex items-center justify-between px-3 py-2 rounded-xl text-[11px] text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-slate-200 hover:bg-slate-50 dark:hover:bg-slate-800/50 transition-colors"
              >
                <div className="flex items-center gap-2">
                  <Sparkles className="w-3.5 h-3.5 text-amber-500" />
                  <span>Spaces as API docs</span>
                </div>
                <ExternalLink className="w-3 h-3 opacity-60" />
              </a>

              <div className="px-3 pt-1 pb-0.5 text-[10px] text-slate-400 dark:text-slate-500 font-mono flex items-center justify-between">
                <span>HF Studio Client</span>
                <span className="flex items-center gap-1">
                  <ShieldCheck className="w-3 h-3 text-emerald-500" />
                  ZeroGPU Ready
                </span>
              </div>
            </div>
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  );
};

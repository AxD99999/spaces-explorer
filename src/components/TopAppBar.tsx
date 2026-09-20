import React, { useState } from 'react';
import { Smartphone, Monitor, MoreVertical, ExternalLink, Sun, Moon, RotateCcw } from 'lucide-react';
import { motion } from 'motion/react';

interface TopAppBarProps {
  onRefresh?: () => void;
  isLoading: boolean;
  spaceCount: number;
  activeCount: number;
  isPhoneFrame: boolean;
  onTogglePhoneFrame: () => void;
  theme: 'light' | 'dark';
  onToggleTheme: () => void;
}

export const TopAppBar: React.FC<TopAppBarProps> = ({
  onRefresh,
  isLoading,
  spaceCount,
  activeCount,
  isPhoneFrame,
  onTogglePhoneFrame,
  theme,
  onToggleTheme,
}) => {
  const [showMenu, setShowMenu] = useState(false);

  return (
    <header className="sticky top-0 z-30 bg-white/90 dark:bg-slate-900/90 backdrop-blur-md border-b border-slate-200/80 dark:border-slate-800/80 transition-colors">
      {/* Subtle loading indicator */}
      {isLoading && (
        <div className="absolute top-0 left-0 right-0 h-0.5 bg-amber-500/20 overflow-hidden">
          <motion.div
            className="h-full bg-amber-500"
            initial={{ x: '-100%' }}
            animate={{ x: '100%' }}
            transition={{ repeat: Infinity, duration: 1.1, ease: 'easeInOut' }}
          />
        </div>
      )}

      <div className="px-4 py-3 flex items-center justify-between gap-3">
        {/* Left Branding - Minimal & Elegant */}
        <div className="flex items-center gap-2.5 min-w-0">
          <div className="w-8 h-8 rounded-xl bg-slate-100 dark:bg-slate-800 border border-slate-200 dark:border-slate-700/70 flex items-center justify-center text-base select-none shrink-0 shadow-xs">
            🤗
          </div>
          <div className="min-w-0">
            <h1 className="text-sm sm:text-base font-semibold text-slate-900 dark:text-slate-100 tracking-tight truncate leading-tight">
              Hugging Face Spaces
            </h1>
            <p className="text-[11px] text-slate-500 dark:text-slate-400 font-medium truncate">
              {spaceCount > 0 ? (
                <>
                  <span>{spaceCount} spaces</span>
                  <span className="mx-1.5 opacity-40">•</span>
                  <span className="text-emerald-600 dark:text-emerald-400">{activeCount} running</span>
                </>
              ) : (
                'Discover community spaces'
              )}
            </p>
          </div>
        </div>

        {/* Right Actions */}
        <div className="flex items-center gap-1 shrink-0">
          {/* Light / Dark Mode Toggle */}
          <button
            id="btn-theme-toggle"
            onClick={onToggleTheme}
            className="p-2 rounded-xl text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-100 hover:bg-slate-100 dark:hover:bg-slate-800 active:scale-95 transition-all cursor-pointer"
            aria-label={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
            title={theme === 'dark' ? 'Light mode' : 'Dark mode'}
          >
            {theme === 'dark' ? (
              <Sun className="w-4 h-4 text-amber-400" />
            ) : (
              <Moon className="w-4 h-4 text-slate-700" />
            )}
          </button>

          {/* Screen Frame Toggle (Desktop / Phone Frame) */}
          <button
            id="btn-toggle-frame-mode"
            onClick={onTogglePhoneFrame}
            className="p-2 rounded-xl text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-100 hover:bg-slate-100 dark:hover:bg-slate-800 active:scale-95 transition-all hidden sm:flex cursor-pointer"
            aria-label={isPhoneFrame ? 'Expand view' : 'Fit to phone frame'}
            title={isPhoneFrame ? 'Expand view' : 'Fit to phone frame'}
          >
            {isPhoneFrame ? <Monitor className="w-4 h-4" /> : <Smartphone className="w-4 h-4" />}
          </button>

          {/* Overflow Menu */}
          <div className="relative">
            <button
              id="btn-appbar-overflow-menu"
              onClick={() => setShowMenu(!showMenu)}
              className="p-2 rounded-xl text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-100 hover:bg-slate-100 dark:hover:bg-slate-800 active:scale-95 transition-all cursor-pointer"
              aria-label="More options"
            >
              <MoreVertical className="w-4 h-4" />
            </button>

            {showMenu && (
              <>
                <div
                  className="fixed inset-0 z-40"
                  onClick={() => setShowMenu(false)}
                />
                <div className="absolute right-0 mt-1.5 w-60 rounded-2xl bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 shadow-xl py-1.5 z-50 text-xs text-slate-700 dark:text-slate-200 animate-in fade-in zoom-in-95 duration-100">
                  <div className="px-3.5 py-2.5 border-b border-slate-100 dark:border-slate-700/60">
                    <p className="font-semibold text-slate-900 dark:text-slate-100">Hugging Face Explorer</p>
                    <p className="text-[11px] text-slate-500 dark:text-slate-400 mt-0.5">
                      Real-time spaces with smart pagination and category search.
                    </p>
                  </div>

                  {onRefresh && (
                    <button
                      onClick={() => {
                        setShowMenu(false);
                        onRefresh();
                      }}
                      className="w-full px-3.5 py-2.5 flex items-center gap-2.5 text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700/60 transition-colors text-left cursor-pointer"
                    >
                      <RotateCcw className={`w-3.5 h-3.5 text-slate-400 ${isLoading ? 'animate-spin text-amber-500' : ''}`} />
                      <span>Refresh spaces list</span>
                    </button>
                  )}

                  <a
                    href="https://huggingface.co/spaces"
                    target="_blank"
                    rel="noopener noreferrer"
                    className="w-full px-3.5 py-2.5 flex items-center gap-2.5 text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700/60 transition-colors text-left"
                  >
                    <ExternalLink className="w-3.5 h-3.5 text-slate-400" />
                    <span>Open Hugging Face Spaces</span>
                  </a>
                </div>
              </>
            )}
          </div>
        </div>
      </div>
    </header>
  );
};

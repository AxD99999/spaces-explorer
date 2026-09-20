import React from 'react';
import { Menu, Smartphone, Monitor, Sun, Moon } from 'lucide-react';
import { motion } from 'motion/react';

interface TopAppBarProps {
  onOpenMenu: () => void;
  onRefresh?: () => void;
  isLoading: boolean;
  spaceCount: number;
  activeCount: number;
  isPhoneFrame: boolean;
  onTogglePhoneFrame: () => void;
  theme: 'light' | 'dark';
  onToggleTheme: () => void;
  favouritesCount?: number;
  runningTasksCount?: number;
}

export const TopAppBar: React.FC<TopAppBarProps> = ({
  onOpenMenu,
  isLoading,
  spaceCount,
  activeCount,
  isPhoneFrame,
  onTogglePhoneFrame,
  theme,
  onToggleTheme,
  runningTasksCount = 0,
}) => {
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

      <div className="px-3.5 py-2.5 flex items-center justify-between gap-3">
        {/* Left: Hamburger menu + App identity */}
        <div className="flex items-center gap-2.5 min-w-0">
          <button
            id="btn-hamburger-menu"
            onClick={onOpenMenu}
            className="p-2 -ml-1 rounded-xl text-slate-700 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800 active:scale-95 transition-all cursor-pointer relative"
            aria-label="Open navigation menu"
            title="Navigation menu"
          >
            <Menu className="w-5 h-5" />
            {runningTasksCount > 0 && (
              <span className="absolute top-1.5 right-1.5 w-2 h-2 rounded-full bg-emerald-500 animate-ping" />
            )}
            {runningTasksCount > 0 && (
              <span className="absolute top-1.5 right-1.5 w-2 h-2 rounded-full bg-emerald-500 ring-2 ring-white dark:ring-slate-900" />
            )}
          </button>

          <div className="w-8 h-8 rounded-xl bg-slate-100 dark:bg-slate-800 border border-slate-200 dark:border-slate-700/70 flex items-center justify-center text-base select-none shrink-0 shadow-xs">
            🤗
          </div>

          <div className="min-w-0">
            <h1 className="text-sm font-semibold text-slate-900 dark:text-slate-100 tracking-tight truncate leading-tight">
              Hugging Face Spaces
            </h1>
            <p className="text-[11px] text-slate-500 dark:text-slate-400 font-medium truncate">
              {spaceCount > 0 ? (
                <>
                  <span>{spaceCount} spaces</span>
                  <span className="mx-1.5 opacity-40">&bull;</span>
                  <span className="text-emerald-600 dark:text-emerald-400">{activeCount} running</span>
                </>
              ) : (
                'Discover community spaces'
              )}
            </p>
          </div>
        </div>

        {/* Right Actions: Light/Dark mode and Frame Toggle */}
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
        </div>
      </div>
    </header>
  );
};

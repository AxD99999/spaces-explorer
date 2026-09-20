import React, { useState, useEffect } from 'react';
import { Wifi, Signal, Battery, ChevronLeft, Square } from 'lucide-react';

interface AndroidFrameProps {
  children: React.ReactNode;
  isPhoneFrame: boolean;
  onToggleFrame: () => void;
  onSystemBack?: () => void;
  canGoBack?: boolean;
}

export const AndroidFrame: React.FC<AndroidFrameProps> = ({
  children,
  isPhoneFrame,
  onToggleFrame,
  onSystemBack,
  canGoBack = false,
}) => {
  const [currentTime, setCurrentTime] = useState('9:41');

  useEffect(() => {
    const updateTime = () => {
      const now = new Date();
      setCurrentTime(
        now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false })
      );
    };
    updateTime();
    const interval = setInterval(updateTime, 10000);
    return () => clearInterval(interval);
  }, []);

  return (
    <div className="min-h-screen bg-slate-100 dark:bg-slate-950 text-slate-900 dark:text-slate-100 flex flex-col items-center justify-center p-0 sm:p-4 select-none overflow-x-hidden transition-colors">
      {/* Outer Shell container */}
      <div
        className={`w-full transition-all duration-300 flex flex-col ${
          isPhoneFrame
            ? 'max-w-[440px] h-[95vh] max-h-[890px] rounded-[40px] border-[8px] border-slate-300 dark:border-slate-800 shadow-xl dark:shadow-[0_25px_70px_rgba(0,0,0,0.8)] overflow-hidden relative'
            : 'max-w-4xl min-h-screen sm:min-h-[92vh] sm:rounded-2xl border sm:border-slate-200 dark:sm:border-slate-800 shadow-xl overflow-hidden relative'
        }`}
      >
        {/* Android Status Bar */}
        <div
          id="android-status-bar"
          className="bg-white/90 dark:bg-slate-900/90 backdrop-blur-md px-6 py-1.5 flex items-center justify-between text-xs text-slate-700 dark:text-slate-300 shrink-0 z-40 border-b border-slate-200/60 dark:border-slate-800/40 select-none transition-colors"
        >
          {/* Clock */}
          <div className="font-semibold text-xs tracking-tight font-mono text-slate-800 dark:text-slate-200">
            {currentTime}
          </div>

          {/* Android Punch Hole Camera (if in phone frame) */}
          {isPhoneFrame && (
            <div className="w-3.5 h-3.5 rounded-full bg-slate-200 dark:bg-slate-950 ring-1 ring-slate-300 dark:ring-slate-800 flex items-center justify-center">
              <div className="w-1.5 h-1.5 rounded-full bg-slate-400 dark:bg-slate-900" />
            </div>
          )}

          {/* System Status Icons */}
          <div className="flex items-center gap-2 text-slate-600 dark:text-slate-300">
            <Signal className="w-3.5 h-3.5" />
            <Wifi className="w-3.5 h-3.5" />
            <div className="flex items-center gap-1 font-mono text-[10px] text-slate-500 dark:text-slate-400">
              <Battery className="w-4 h-4 text-emerald-600 dark:text-emerald-400" />
              <span>98%</span>
            </div>
          </div>
        </div>

        {/* App Content Canvas */}
        <div className="flex-1 flex flex-col bg-slate-50 dark:bg-slate-900 overflow-hidden relative transition-colors">
          {children}
        </div>

        {/* Android Navigation Bar */}
        <div
          id="android-nav-bar"
          className="bg-white/95 dark:bg-slate-900/95 backdrop-blur-md px-8 py-2 flex items-center justify-around shrink-0 border-t border-slate-200/80 dark:border-slate-800/60 z-30 select-none transition-colors"
        >
          {/* Android Back Button */}
          <button
            id="android-system-back"
            onClick={onSystemBack}
            disabled={!canGoBack}
            className={`p-1.5 rounded-full transition-all active:scale-90 ${
              canGoBack
                ? 'text-slate-700 dark:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 cursor-pointer'
                : 'text-slate-300 dark:text-slate-700 opacity-40 cursor-default'
            }`}
            title="System Back"
            aria-label="Android Back"
          >
            <ChevronLeft className="w-4 h-4" />
          </button>

          {/* Android Home Gesture Pill */}
          <button
            id="android-system-home"
            onClick={onSystemBack}
            className="w-14 h-1 rounded-full bg-slate-400 dark:bg-slate-600 hover:bg-slate-600 dark:hover:bg-slate-400 active:scale-95 transition-all cursor-pointer"
            title="System Home"
            aria-label="Android Home"
          />

          {/* Android Recents / Frame Toggle Button */}
          <button
            id="android-system-recents"
            onClick={onToggleFrame}
            className="p-1.5 rounded-full text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 active:scale-90 transition-all cursor-pointer"
            title="Toggle View Mode"
            aria-label="Toggle View Mode"
          >
            <Square className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>
    </div>
  );
};

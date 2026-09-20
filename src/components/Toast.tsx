import React, { useEffect } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { CheckCircle2, AlertTriangle, Info, XCircle, X } from 'lucide-react';
import { ToastMessage } from '../types';

interface ToastProps {
  toast: ToastMessage | null;
  onDismiss: () => void;
}

export const Toast: React.FC<ToastProps> = ({ toast, onDismiss }) => {
  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => {
      onDismiss();
    }, 4000);
    return () => clearTimeout(timer);
  }, [toast, onDismiss]);

  return (
    <AnimatePresence>
      {toast && (
        <motion.div
          id="android-snackbar"
          initial={{ opacity: 0, y: 30, scale: 0.95 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          exit={{ opacity: 0, y: 20, scale: 0.95 }}
          transition={{ duration: 0.25, ease: 'easeOut' }}
          className="fixed bottom-16 sm:bottom-8 left-4 right-4 max-w-md mx-auto z-50 pointer-events-auto"
        >
          <div className="bg-slate-800/95 text-slate-100 backdrop-blur-md px-4 py-3 rounded-2xl shadow-2xl border border-slate-700/60 flex items-center justify-between gap-3 text-sm">
            <div className="flex items-center gap-2.5 min-w-0">
              {toast.type === 'success' && <CheckCircle2 className="w-5 h-5 text-emerald-400 shrink-0" />}
              {toast.type === 'warning' && <AlertTriangle className="w-5 h-5 text-amber-400 shrink-0" />}
              {toast.type === 'error' && <XCircle className="w-5 h-5 text-rose-400 shrink-0" />}
              {(!toast.type || toast.type === 'info') && <Info className="w-5 h-5 text-sky-400 shrink-0" />}
              <span className="truncate text-slate-200 font-medium">{toast.message}</span>
            </div>

            <div className="flex items-center gap-2 shrink-0">
              {toast.actionLabel && toast.onAction && (
                <button
                  id="android-snackbar-action"
                  onClick={() => {
                    toast.onAction?.();
                    onDismiss();
                  }}
                  className="px-2.5 py-1 text-xs font-semibold uppercase tracking-wider text-amber-400 hover:text-amber-300 active:scale-95 transition-all"
                >
                  {toast.actionLabel}
                </button>
              )}
              <button
                id="android-snackbar-dismiss"
                onClick={onDismiss}
                className="p-1 rounded-full text-slate-400 hover:text-slate-200 hover:bg-slate-700/50 active:scale-95 transition-all"
                aria-label="Close notification"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  );
};

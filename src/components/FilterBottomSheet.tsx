import React from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { X, Check, RotateCcw, SlidersHorizontal, ArrowUpDown, Pin, Sparkles, Cpu, Layers } from 'lucide-react';
import { SpaceFilterState, HF_SEMANTIC_CATEGORIES } from '../types';

interface FilterBottomSheetProps {
  isOpen: boolean;
  onClose: () => void;
  filter: SpaceFilterState;
  onChange: (updates: Partial<SpaceFilterState>) => void;
  availableCategories: string[];
  availableHardwares: string[];
  availableAuthors: string[];
  availableSdks: string[];
  totalResults: number;
}

export const FilterBottomSheet: React.FC<FilterBottomSheetProps> = ({
  isOpen,
  onClose,
  filter,
  onChange,
  availableHardwares,
  availableAuthors,
  availableSdks,
  totalResults,
}) => {
  if (!isOpen) return null;

  const handleReset = () => {
    onChange({
      search: '',
      status: 'all',
      sdk: 'all',
      category: 'all',
      hardware: 'all',
      author: 'all',
      pinnedOnly: false,
      featuredOnly: false,
      sortBy: 'trending-desc',
    });
  };

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-0 sm:p-4">
        {/* Backdrop */}
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={onClose}
          className="fixed inset-0 bg-slate-950/60 dark:bg-slate-950/80 backdrop-blur-xs"
        />

        {/* Bottom Sheet Container */}
        <motion.div
          id="android-filter-bottom-sheet"
          initial={{ y: '100%' }}
          animate={{ y: 0 }}
          exit={{ y: '100%' }}
          transition={{ type: 'spring', damping: 28, stiffness: 300 }}
          className="relative w-full max-w-lg max-h-[85vh] bg-white dark:bg-slate-900 rounded-t-3xl sm:rounded-2xl border border-slate-200 dark:border-slate-800 shadow-2xl flex flex-col z-10 overflow-hidden text-slate-900 dark:text-slate-100"
        >
          {/* Mobile Handle Bar */}
          <div className="w-full pt-3 pb-1 flex justify-center sm:hidden">
            <div className="w-10 h-1 rounded-full bg-slate-300 dark:bg-slate-700" />
          </div>

          {/* Sheet Header */}
          <div className="px-5 py-3.5 border-b border-slate-100 dark:border-slate-800 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <SlidersHorizontal className="w-4 h-4 text-slate-700 dark:text-slate-300" />
              <h3 className="text-sm font-semibold tracking-tight">
                Filter & Sort
              </h3>
            </div>

            <div className="flex items-center gap-1.5">
              <button
                id="btn-filter-reset"
                onClick={handleReset}
                className="flex items-center gap-1 px-2.5 py-1 rounded-lg text-xs font-medium text-slate-500 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors cursor-pointer"
              >
                <RotateCcw className="w-3 h-3" />
                <span>Reset</span>
              </button>
              <button
                id="btn-filter-close"
                onClick={onClose}
                className="p-1.5 rounded-full text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors cursor-pointer"
                aria-label="Close filters"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
          </div>

          {/* Scrollable Filter Options */}
          <div className="flex-1 overflow-y-auto p-5 space-y-5 scrollbar-thin text-xs">
            {/* Sort Order */}
            <div className="space-y-2">
              <label className="font-semibold text-slate-700 dark:text-slate-300 flex items-center gap-1.5">
                <ArrowUpDown className="w-3.5 h-3.5 text-slate-500 dark:text-slate-400" />
                <span>Sort by</span>
              </label>
              <div className="grid grid-cols-2 gap-2">
                {[
                  { id: 'trending-desc', label: 'Trending' },
                  { id: 'likes-desc', label: 'Most Liked' },
                  { id: 'recent-desc', label: 'Recently Modified' },
                  { id: 'created-desc', label: 'Recently Created' },
                  { id: 'name-asc', label: 'Title (A → Z)' },
                  { id: 'name-desc', label: 'Title (Z → A)' },
                ].map((opt) => (
                  <button
                    key={opt.id}
                    onClick={() => onChange({ sortBy: opt.id as any })}
                    className={`p-2.5 rounded-xl border text-left font-medium transition-all cursor-pointer ${
                      filter.sortBy === opt.id
                        ? 'bg-slate-900 dark:bg-white text-white dark:text-slate-950 border-slate-900 dark:border-white font-semibold shadow-2xs'
                        : 'bg-slate-50 dark:bg-slate-800/60 text-slate-600 dark:text-slate-300 border-slate-200 dark:border-slate-700 hover:bg-slate-100 dark:hover:bg-slate-800'
                    }`}
                  >
                    {opt.label}
                  </button>
                ))}
              </div>
            </div>

            {/* Runtime Status */}
            <div className="space-y-2">
              <label className="font-semibold text-slate-700 dark:text-slate-300">
                Runtime Status
              </label>
              <div className="grid grid-cols-3 gap-2">
                {[
                  { id: 'all', label: 'All' },
                  { id: 'running', label: 'Running' },
                  { id: 'sleeping', label: 'Sleeping' },
                ].map((s) => (
                  <button
                    key={s.id}
                    onClick={() => onChange({ status: s.id as any })}
                    className={`p-2 rounded-xl border text-center font-medium transition-all cursor-pointer ${
                      filter.status === s.id
                        ? 'bg-slate-900 dark:bg-white text-white dark:text-slate-950 border-slate-900 dark:border-white font-semibold shadow-2xs'
                        : 'bg-slate-50 dark:bg-slate-800/60 text-slate-600 dark:text-slate-300 border-slate-200 dark:border-slate-700 hover:bg-slate-100 dark:hover:bg-slate-800'
                    }`}
                  >
                    {s.label}
                  </button>
                ))}
              </div>
            </div>

            {/* AI Category */}
            <div className="space-y-2">
              <label className="font-semibold text-slate-700 dark:text-slate-300 flex items-center gap-1.5">
                <Sparkles className="w-3.5 h-3.5 text-amber-500" />
                <span>AI Category</span>
              </label>
              <div className="flex flex-wrap gap-1.5">
                <button
                  onClick={() => onChange({ category: 'all' })}
                  className={`px-3 py-1.5 rounded-xl border font-medium transition-all cursor-pointer ${
                    filter.category === 'all'
                      ? 'bg-slate-900 dark:bg-white text-white dark:text-slate-950 border-slate-900 dark:border-white font-semibold'
                      : 'bg-slate-50 dark:bg-slate-800/60 text-slate-600 dark:text-slate-300 border-slate-200 dark:border-slate-700 hover:bg-slate-100 dark:hover:bg-slate-800'
                  }`}
                >
                  All Categories
                </button>
                {HF_SEMANTIC_CATEGORIES.map((cat) => (
                  <button
                    key={cat.id}
                    onClick={() => onChange({ category: filter.category === cat.id ? 'all' : cat.id })}
                    className={`inline-flex items-center gap-1 px-3 py-1.5 rounded-xl border font-medium transition-all cursor-pointer ${
                      filter.category === cat.id
                        ? 'bg-slate-900 dark:bg-white text-white dark:text-slate-950 border-slate-900 dark:border-white font-semibold'
                        : 'bg-slate-50 dark:bg-slate-800/60 text-slate-600 dark:text-slate-300 border-slate-200 dark:border-slate-700 hover:bg-slate-100 dark:hover:bg-slate-800'
                    }`}
                  >
                    <span>{cat.emoji}</span>
                    <span>{cat.name}</span>
                  </button>
                ))}
              </div>
            </div>

            {/* SDK Type */}
            <div className="space-y-2">
              <label className="font-semibold text-slate-700 dark:text-slate-300 flex items-center gap-1.5">
                <Layers className="w-3.5 h-3.5 text-sky-500" />
                <span>SDK Framework</span>
              </label>
              <div className="flex flex-wrap gap-1.5">
                {['all', ...availableSdks].map((sdk) => (
                  <button
                    key={sdk}
                    onClick={() => onChange({ sdk })}
                    className={`px-3 py-1.5 rounded-xl border capitalize font-medium transition-all cursor-pointer ${
                      filter.sdk === sdk
                        ? 'bg-slate-900 dark:bg-white text-white dark:text-slate-950 border-slate-900 dark:border-white font-semibold'
                        : 'bg-slate-50 dark:bg-slate-800/60 text-slate-600 dark:text-slate-300 border-slate-200 dark:border-slate-700 hover:bg-slate-100 dark:hover:bg-slate-800'
                    }`}
                  >
                    {sdk === 'all' ? 'All SDKs' : sdk}
                  </button>
                ))}
              </div>
            </div>

            {/* Hardware */}
            {availableHardwares.length > 0 && (
              <div className="space-y-2">
                <label className="font-semibold text-slate-700 dark:text-slate-300 flex items-center gap-1.5">
                  <Cpu className="w-3.5 h-3.5 text-purple-500" />
                  <span>Hardware Accelerator</span>
                </label>
                <div className="flex flex-wrap gap-1.5">
                  <button
                    onClick={() => onChange({ hardware: 'all' })}
                    className={`px-3 py-1.5 rounded-xl border font-medium transition-all cursor-pointer ${
                      filter.hardware === 'all'
                        ? 'bg-slate-900 dark:bg-white text-white dark:text-slate-950 border-slate-900 dark:border-white font-semibold'
                        : 'bg-slate-50 dark:bg-slate-800/60 text-slate-600 dark:text-slate-300 border-slate-200 dark:border-slate-700 hover:bg-slate-100 dark:hover:bg-slate-800'
                    }`}
                  >
                    All Hardware
                  </button>
                  {availableHardwares.map((hw) => (
                    <button
                      key={hw}
                      onClick={() => onChange({ hardware: hw })}
                      className={`px-3 py-1.5 rounded-xl border font-mono transition-all cursor-pointer ${
                        filter.hardware === hw
                          ? 'bg-slate-900 dark:bg-white text-white dark:text-slate-950 border-slate-900 dark:border-white font-semibold'
                          : 'bg-slate-50 dark:bg-slate-800/60 text-slate-600 dark:text-slate-300 border-slate-200 dark:border-slate-700 hover:bg-slate-100 dark:hover:bg-slate-800'
                      }`}
                    >
                      {hw}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {/* Author / Organization */}
            {availableAuthors.length > 0 && (
              <div className="space-y-2">
                <label className="font-semibold text-slate-700 dark:text-slate-300">
                  Creator / Organization
                </label>
                <div className="flex flex-wrap gap-1.5 max-h-36 overflow-y-auto scrollbar-thin">
                  <button
                    onClick={() => onChange({ author: 'all' })}
                    className={`px-2.5 py-1 rounded-lg border text-[11px] font-mono transition-all cursor-pointer ${
                      filter.author === 'all'
                        ? 'bg-slate-900 dark:bg-white text-white dark:text-slate-950 border-slate-900 dark:border-white font-semibold'
                        : 'bg-slate-50 dark:bg-slate-800/60 text-slate-600 dark:text-slate-300 border-slate-200 dark:border-slate-700 hover:bg-slate-100 dark:hover:bg-slate-800'
                    }`}
                  >
                    All Creators
                  </button>
                  {availableAuthors.map((auth) => (
                    <button
                      key={auth}
                      onClick={() => onChange({ author: auth })}
                      className={`px-2.5 py-1 rounded-lg border text-[11px] font-mono transition-all cursor-pointer ${
                        filter.author === auth
                          ? 'bg-slate-900 dark:bg-white text-white dark:text-slate-950 border-slate-900 dark:border-white font-semibold'
                          : 'bg-slate-50 dark:bg-slate-800/60 text-slate-600 dark:text-slate-300 border-slate-200 dark:border-slate-700 hover:bg-slate-100 dark:hover:bg-slate-800'
                      }`}
                    >
                      @{auth}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {/* Highlights */}
            <div className="space-y-2 pt-2 border-t border-slate-100 dark:border-slate-800">
              <label className="font-semibold text-slate-700 dark:text-slate-300 block">
                Highlights
              </label>

              <div className="grid grid-cols-2 gap-2">
                <label
                  onClick={() => onChange({ pinnedOnly: !filter.pinnedOnly })}
                  className={`p-3 rounded-xl border flex items-center justify-between cursor-pointer transition-all ${
                    filter.pinnedOnly
                      ? 'bg-amber-500/10 border-amber-500/30 text-amber-900 dark:text-amber-200'
                      : 'bg-slate-50 dark:bg-slate-800/50 border-slate-200 dark:border-slate-700 text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800'
                  }`}
                >
                  <div className="flex items-center gap-2">
                    <Pin className="w-3.5 h-3.5 text-amber-500" />
                    <span className="font-medium">Pinned Only</span>
                  </div>
                  <div
                    className={`w-4 h-4 rounded border flex items-center justify-center ${
                      filter.pinnedOnly
                        ? 'bg-amber-500 border-amber-500 text-slate-950'
                        : 'border-slate-300 dark:border-slate-600'
                    }`}
                  >
                    {filter.pinnedOnly && <Check className="w-3 h-3 stroke-[3]" />}
                  </div>
                </label>

                <label
                  onClick={() => onChange({ featuredOnly: !filter.featuredOnly })}
                  className={`p-3 rounded-xl border flex items-center justify-between cursor-pointer transition-all ${
                    filter.featuredOnly
                      ? 'bg-sky-500/10 border-sky-500/30 text-sky-900 dark:text-sky-200'
                      : 'bg-slate-50 dark:bg-slate-800/50 border-slate-200 dark:border-slate-700 text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800'
                  }`}
                >
                  <div className="flex items-center gap-2">
                    <Sparkles className="w-3.5 h-3.5 text-sky-500" />
                    <span className="font-medium">Featured Only</span>
                  </div>
                  <div
                    className={`w-4 h-4 rounded border flex items-center justify-center ${
                      filter.featuredOnly
                        ? 'bg-sky-500 border-sky-500 text-slate-950'
                        : 'border-slate-300 dark:border-slate-600'
                    }`}
                  >
                    {filter.featuredOnly && <Check className="w-3 h-3 stroke-[3]" />}
                  </div>
                </label>
              </div>
            </div>
          </div>

          {/* Bottom Action Button */}
          <div className="p-4 border-t border-slate-100 dark:border-slate-800 bg-white dark:bg-slate-900 flex items-center justify-between gap-3">
            <span className="text-xs text-slate-500 dark:text-slate-400">
              <strong className="text-slate-900 dark:text-slate-100">{totalResults}</strong> matching spaces
            </span>

            <button
              id="btn-apply-filters"
              onClick={onClose}
              className="px-5 py-2 rounded-xl bg-slate-900 dark:bg-white text-white dark:text-slate-950 font-semibold text-xs hover:opacity-90 active:scale-95 transition-all cursor-pointer shadow-xs"
            >
              Show Results
            </button>
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
};

import React from 'react';
import { Search, X, SlidersHorizontal } from 'lucide-react';
import { SpaceFilterState, HF_SEMANTIC_CATEGORIES } from '../types';

interface SearchBarProps {
  filter: SpaceFilterState;
  onChange: (updates: Partial<SpaceFilterState>) => void;
  onOpenFilterSheet: () => void;
  activeFilterCount: number;
  totalSpaces: number;
  filteredCount: number;
}

export const SearchBar: React.FC<SearchBarProps> = ({
  filter,
  onChange,
  onOpenFilterSheet,
  activeFilterCount,
  totalSpaces,
  filteredCount,
}) => {
  return (
    <div id="search-filter-section" className="px-4 py-2.5 space-y-2.5">
      {/* Minimalist Search Input */}
      <div className="relative flex items-center">
        <div className="absolute left-3.5 text-slate-400 dark:text-slate-500 pointer-events-none flex items-center">
          <Search className="w-4 h-4" />
        </div>
        <input
          id="input-space-search"
          type="text"
          value={filter.search}
          onChange={(e) => onChange({ search: e.target.value })}
          placeholder="Search spaces, creators, models..."
          className="w-full pl-10 pr-24 py-2 bg-white dark:bg-slate-800/90 hover:bg-slate-50 dark:hover:bg-slate-800 focus:bg-white dark:focus:bg-slate-800 border border-slate-200 dark:border-slate-700/70 focus:border-slate-400 dark:focus:border-slate-500 rounded-xl text-sm text-slate-900 dark:text-slate-100 placeholder-slate-400 dark:placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-slate-400/20 transition-all shadow-2xs"
        />

        <div className="absolute right-2.5 flex items-center gap-1.5">
          {filter.search && (
            <button
              id="btn-clear-search"
              onClick={() => onChange({ search: '' })}
              className="p-1 rounded-full text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-700/60 active:scale-95 transition-all"
              aria-label="Clear search"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          )}

          <button
            id="btn-open-filters"
            onClick={onOpenFilterSheet}
            className={`flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-medium transition-all cursor-pointer ${
              activeFilterCount > 0
                ? 'bg-slate-900 dark:bg-white text-white dark:text-slate-950 font-semibold shadow-2xs'
                : 'bg-slate-100 dark:bg-slate-700/60 text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700'
            }`}
            aria-label="Open filter menu"
          >
            <SlidersHorizontal className="w-3 h-3" />
            <span>Filters</span>
            {activeFilterCount > 0 && (
              <span className="w-4 h-4 rounded-full bg-amber-500 text-slate-950 text-[10px] font-bold flex items-center justify-center">
                {activeFilterCount}
              </span>
            )}
          </button>
        </div>
      </div>

      {/* Category Pills (Minimalist horizontal scrolling list without cluttering labels) */}
      <div className="flex items-center gap-1.5 overflow-x-auto pb-0.5 no-scrollbar text-xs">
        <button
          onClick={() => onChange({ category: 'all' })}
          className={`px-3 py-1 rounded-full font-medium whitespace-nowrap transition-all cursor-pointer ${
            filter.category === 'all'
              ? 'bg-slate-900 dark:bg-white text-white dark:text-slate-950 font-semibold shadow-2xs'
              : 'bg-white dark:bg-slate-800/80 text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-200 border border-slate-200/80 dark:border-slate-700/60 hover:bg-slate-50 dark:hover:bg-slate-800'
          }`}
        >
          All
        </button>

        {HF_SEMANTIC_CATEGORIES.map((cat) => {
          const isSelected = filter.category === cat.id;
          return (
            <button
              key={cat.id}
              onClick={() => onChange({ category: isSelected ? 'all' : cat.id })}
              className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full font-medium whitespace-nowrap transition-all cursor-pointer ${
                isSelected
                  ? 'bg-slate-900 dark:bg-white text-white dark:text-slate-950 font-semibold shadow-2xs'
                  : 'bg-white dark:bg-slate-800/80 text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-200 border border-slate-200/80 dark:border-slate-700/60 hover:bg-slate-50 dark:hover:bg-slate-800'
              }`}
            >
              <span className="text-xs">{cat.emoji}</span>
              <span>{cat.name}</span>
            </button>
          );
        })}
      </div>

      {/* Subtle Results & Active Filter Status */}
      <div className="flex items-center justify-between text-[11px] text-slate-500 dark:text-slate-400 px-0.5 pt-0.5">
        <span>
          {filteredCount} {filteredCount === 1 ? 'space' : 'spaces'} {filteredCount !== totalSpaces && `of ${totalSpaces}`}
        </span>
        {activeFilterCount > 0 && (
          <button
            onClick={() =>
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
              })
            }
            className="text-amber-600 dark:text-amber-400 hover:underline cursor-pointer font-medium"
          >
            Clear filters
          </button>
        )}
      </div>
    </div>
  );
};

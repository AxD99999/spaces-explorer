import React from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';

interface PaginationBarProps {
  currentPage: number; // 0-indexed
  totalPages: number | null; // null if unknown / API endless
  totalCount: number;
  pageSize: number;
  isLoading: boolean;
  onPageChange: (newPage: number) => void;
  isFilterActive: boolean;
}

export const PaginationBar: React.FC<PaginationBarProps> = ({
  currentPage,
  totalPages,
  totalCount,
  pageSize,
  isLoading,
  onPageChange,
  isFilterActive,
}) => {
  // If there are no items or only 1 page in filtered mode with <= pageSize items,
  // we can show a minimal count or hide the buttons if totalPages === 1
  if (totalCount === 0) {
    return null;
  }

  const startItem = currentPage * pageSize + 1;
  const endItem = Math.min((currentPage + 1) * pageSize, totalCount);

  const canGoPrev = currentPage > 0 && !isLoading;
  const canGoNext = !isLoading && (totalPages !== null ? currentPage < totalPages - 1 : totalCount > 0);

  return (
    <div
      id="pagination-controller"
      className="mx-4 my-1.5 px-3 py-1.5 rounded-xl bg-white dark:bg-slate-800/80 border border-slate-200/80 dark:border-slate-700/60 shadow-2xs flex items-center justify-between text-xs text-slate-700 dark:text-slate-300 select-none transition-colors"
    >
      {/* Previous Button */}
      <button
        id="btn-pagination-prev"
        onClick={() => onPageChange(currentPage - 1)}
        disabled={!canGoPrev}
        className="flex items-center gap-1 px-2.5 py-1 rounded-lg font-medium text-slate-700 dark:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-700 active:scale-95 disabled:opacity-30 disabled:pointer-events-none transition-all cursor-pointer"
        aria-label="Previous page"
      >
        <ChevronLeft className="w-3.5 h-3.5" />
        <span>Prev</span>
      </button>

      {/* Page Info */}
      <div className="flex flex-col items-center leading-tight text-center">
        <div className="font-semibold text-slate-900 dark:text-slate-100 text-xs flex items-center gap-1.5">
          <span>Page {currentPage + 1}</span>
          {totalPages !== null && (
            <span className="text-slate-400 dark:text-slate-500 font-normal">of {totalPages}</span>
          )}
        </div>
        <span className="text-[10px] text-slate-500 dark:text-slate-400">
          {totalCount > 0 ? `${startItem}–${endItem} of ${totalCount}` : '24 per page'}
        </span>
      </div>

      {/* Next Button */}
      <button
        id="btn-pagination-next"
        onClick={() => onPageChange(currentPage + 1)}
        disabled={!canGoNext}
        className="flex items-center gap-1 px-2.5 py-1 rounded-lg font-medium text-slate-700 dark:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-700 active:scale-95 disabled:opacity-30 disabled:pointer-events-none transition-all cursor-pointer"
        aria-label="Next page"
      >
        <span>Next</span>
        <ChevronRight className="w-3.5 h-3.5" />
      </button>
    </div>
  );
};

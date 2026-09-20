import React, { useState, useMemo, useCallback, useEffect, useRef } from 'react';
import { HfSpace, SpaceFilterState, ToastMessage } from './types';
import { fetchHfSpaces, fetchHfSemanticSearch } from './services/api';
import { AndroidFrame } from './components/AndroidFrame';
import { TopAppBar } from './components/TopAppBar';
import { SearchBar } from './components/SearchBar';
import { PaginationBar } from './components/PaginationBar';
import { FilterBottomSheet } from './components/FilterBottomSheet';
import { SpaceCard } from './components/SpaceCard';
import { SpaceDetails } from './components/SpaceDetails';
import { Toast } from './components/Toast';
import { FilterX, Loader2, ChevronLeft, ChevronRight } from 'lucide-react';
import { AnimatePresence } from 'motion/react';

const ITEMS_PER_PAGE = 24;

const INITIAL_FILTER: SpaceFilterState = {
  search: '',
  status: 'all',
  sdk: 'all',
  category: 'all',
  hardware: 'all',
  author: 'all',
  pinnedOnly: false,
  featuredOnly: false,
  sortBy: 'trending-desc',
};

export default function App() {
  const [spaces, setSpaces] = useState<HfSpace[]>([]);
  const [currentPage, setCurrentPage] = useState<number>(0);
  const [frontendPage, setFrontendPage] = useState<number>(0);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [selectedSpace, setSelectedSpace] = useState<HfSpace | null>(null);
  const [filter, setFilter] = useState<SpaceFilterState>(INITIAL_FILTER);
  const [isFilterSheetOpen, setIsFilterSheetOpen] = useState<boolean>(false);
  const [toast, setToast] = useState<ToastMessage | null>(null);
  const [isPhoneFrame, setIsPhoneFrame] = useState<boolean>(true);

  // Theme Management (Light / Dark)
  const [theme, setTheme] = useState<'light' | 'dark'>(() => {
    const saved = localStorage.getItem('hf_theme');
    if (saved === 'dark' || saved === 'light') return saved;
    return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches
      ? 'dark'
      : 'light';
  });

  useEffect(() => {
    const root = document.documentElement;
    if (theme === 'dark') {
      root.classList.add('dark');
    } else {
      root.classList.remove('dark');
    }
    localStorage.setItem('hf_theme', theme);
  }, [theme]);

  const toggleTheme = useCallback(() => {
    setTheme((prev) => (prev === 'dark' ? 'light' : 'dark'));
  }, []);

  const scrollContainerRef = useRef<HTMLDivElement | null>(null);

  const showToast = useCallback(
    (
      message: string,
      type: ToastMessage['type'] = 'info',
      actionLabel?: string,
      onAction?: () => void
    ) => {
      setToast({
        id: Math.random().toString(),
        message,
        type,
        actionLabel,
        onAction,
      });
    },
    []
  );

  // Determine if any filter is active
  const isFilterActive = useMemo(() => {
    return (
      filter.category !== 'all' ||
      filter.search.trim() !== '' ||
      filter.status !== 'all' ||
      filter.sdk !== 'all' ||
      filter.hardware !== 'all' ||
      filter.author !== 'all' ||
      filter.pinnedOnly ||
      filter.featuredOnly ||
      filter.sortBy !== 'trending-desc'
    );
  }, [filter]);

  // Primary live fetch handler: automatically called on load, backend page change, or category filter
  const loadData = useCallback(
    async (pageToLoad: number, categoryFilter: string, searchQuery: string) => {
      setIsLoading(true);
      try {
        if (categoryFilter !== 'all') {
          // API request for semantic category filtering
          const semanticResults = await fetchHfSemanticSearch({
            category: categoryFilter,
            query: searchQuery,
          });
          setSpaces(semanticResults);
        } else {
          // API request for paginated spaces (spaces-json?p=X)
          const result = await fetchHfSpaces(pageToLoad);
          setSpaces(result.spaces);
        }

        // Scroll back to top on data load
        if (scrollContainerRef.current) {
          scrollContainerRef.current.scrollTo({ top: 0, behavior: 'smooth' });
        }
      } catch (err: any) {
        console.error('Error fetching Hugging Face spaces:', err);
        showToast(
          err?.message || 'Failed to fetch spaces from Hugging Face API',
          'error',
          'Retry',
          () => loadData(pageToLoad, categoryFilter, searchQuery)
        );
      } finally {
        setIsLoading(false);
      }
    },
    [showToast]
  );

  // Automatically refresh when the app loads
  useEffect(() => {
    loadData(currentPage, filter.category, filter.search);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Handle backend page change (when no filter is active)
  const handleBackendPageChange = useCallback(
    (newPage: number) => {
      if (newPage < 0 || isLoading) return;
      setCurrentPage(newPage);
      loadData(newPage, 'all', filter.search);
    },
    [isLoading, filter.search, loadData]
  );

  // Handle filter updates
  const handleFilterUpdate = useCallback(
    (updates: Partial<SpaceFilterState>) => {
      // Whenever filters change, reset the frontend page to 0
      setFrontendPage(0);

      setFilter((prev) => {
        const next = { ...prev, ...updates };

        // If category changed, trigger API request
        if (updates.category !== undefined && updates.category !== prev.category) {
          if (updates.category === 'all') {
            loadData(currentPage, 'all', next.search);
          } else {
            loadData(currentPage, updates.category, next.search);
          }
        }

        return next;
      });
    },
    [currentPage, loadData]
  );

  // Manual refresh trigger
  const handleManualRefresh = useCallback(() => {
    loadData(currentPage, filter.category, filter.search);
  }, [currentPage, filter.category, filter.search, loadData]);

  // Compute available filter options dynamically
  const {
    availableCategories,
    availableHardwares,
    availableAuthors,
    availableSdks,
    activeSpacesCount,
  } = useMemo(() => {
    const categories = new Set<string>();
    const hardwares = new Set<string>();
    const authors = new Set<string>();
    const sdks = new Set<string>();
    let active = 0;

    spaces.forEach((s) => {
      if (s.runtime?.stage === 'RUNNING') active++;
      if (s.ai_category) categories.add(s.ai_category);
      if (s.runtime?.hardware?.current) hardwares.add(s.runtime.hardware.current);
      if (s.author) authors.add(s.author);
      if (s.sdk) sdks.add(s.sdk);
    });

    return {
      availableCategories: Array.from(categories).sort(),
      availableHardwares: Array.from(hardwares).sort(),
      availableAuthors: Array.from(authors).sort(),
      availableSdks: Array.from(sdks).sort(),
      activeSpacesCount: active,
    };
  }, [spaces]);

  // Count active non-default filters
  const activeFilterCount = useMemo(() => {
    let count = 0;
    if (filter.status !== 'all') count++;
    if (filter.sdk !== 'all') count++;
    if (filter.category !== 'all') count++;
    if (filter.hardware !== 'all') count++;
    if (filter.author !== 'all') count++;
    if (filter.pinnedOnly) count++;
    if (filter.featuredOnly) count++;
    if (filter.sortBy !== 'trending-desc') count++;
    return count;
  }, [filter]);

  // Filter and sort spaces
  const filteredSpaces = useMemo(() => {
    const searchTerm = filter.search.trim().toLowerCase();

    return spaces
      .filter((s) => {
        if (searchTerm) {
          const matchId = s.id.toLowerCase().includes(searchTerm);
          const matchTitle = s.title?.toLowerCase().includes(searchTerm);
          const matchAuthor = s.author?.toLowerCase().includes(searchTerm);
          const matchDesc = s.shortDescription?.toLowerCase().includes(searchTerm);
          const matchAiDesc = s.ai_short_description?.toLowerCase().includes(searchTerm);
          const matchCategory = s.ai_category?.toLowerCase().includes(searchTerm);
          const matchSdk = s.sdk?.toLowerCase().includes(searchTerm);
          const matchTags = s.tags?.some((t) => t.toLowerCase().includes(searchTerm));

          if (
            !matchId &&
            !matchTitle &&
            !matchAuthor &&
            !matchDesc &&
            !matchAiDesc &&
            !matchCategory &&
            !matchSdk &&
            !matchTags
          ) {
            return false;
          }
        }

        if (filter.status === 'running') {
          if (s.runtime?.stage !== 'RUNNING') return false;
        } else if (filter.status === 'sleeping') {
          if (s.runtime?.stage !== 'SLEEPING') return false;
        }

        if (filter.sdk !== 'all' && s.sdk?.toLowerCase() !== filter.sdk.toLowerCase()) {
          return false;
        }

        if (filter.hardware !== 'all' && s.runtime?.hardware?.current !== filter.hardware) {
          return false;
        }

        if (filter.author !== 'all' && s.author !== filter.author) {
          return false;
        }

        if (filter.pinnedOnly && !s.pinned) {
          return false;
        }

        if (filter.featuredOnly && !s.featured) {
          return false;
        }

        return true;
      })
      .sort((a, b) => {
        switch (filter.sortBy) {
          case 'likes-desc':
            return (b.likes || 0) - (a.likes || 0);
          case 'name-asc':
            return (a.title || a.id).localeCompare(b.title || b.id);
          case 'name-desc':
            return (b.title || b.id).localeCompare(a.title || a.id);
          case 'recent-desc': {
            const timeA = a.lastModified ? new Date(a.lastModified).getTime() : 0;
            const timeB = b.lastModified ? new Date(b.lastModified).getTime() : 0;
            return timeB - timeA;
          }
          case 'created-desc': {
            const timeA = a.createdAt ? new Date(a.createdAt).getTime() : 0;
            const timeB = b.createdAt ? new Date(b.createdAt).getTime() : 0;
            return timeB - timeA;
          }
          case 'trending-desc':
          default:
            return (b.trendingScore || 0) - (a.trendingScore || 0);
        }
      });
  }, [spaces, filter]);

  // Frontend Pagination calculation (Max 24 spaces per page when filters are active)
  const totalFrontendPages = useMemo(() => {
    return Math.max(1, Math.ceil(filteredSpaces.length / ITEMS_PER_PAGE));
  }, [filteredSpaces.length]);

  // Sliced displayed spaces (enforces max 24 spaces per page)
  const displayedSpaces = useMemo(() => {
    if (isFilterActive) {
      const startIndex = frontendPage * ITEMS_PER_PAGE;
      return filteredSpaces.slice(startIndex, startIndex + ITEMS_PER_PAGE);
    }
    // When no filter is active, spaces-json returns 24 per page
    return filteredSpaces.slice(0, ITEMS_PER_PAGE);
  }, [isFilterActive, frontendPage, filteredSpaces]);

  // Page change dispatcher
  const handlePageChange = useCallback(
    (newPage: number) => {
      if (isFilterActive) {
        if (newPage >= 0 && newPage < totalFrontendPages) {
          setFrontendPage(newPage);
          if (scrollContainerRef.current) {
            scrollContainerRef.current.scrollTo({ top: 0, behavior: 'smooth' });
          }
        }
      } else {
        handleBackendPageChange(newPage);
      }
    },
    [isFilterActive, totalFrontendPages, handleBackendPageChange]
  );

  return (
    <AndroidFrame
      isPhoneFrame={isPhoneFrame}
      onToggleFrame={() => setIsPhoneFrame(!isPhoneFrame)}
      onSystemBack={() => {
        if (selectedSpace) {
          setSelectedSpace(null);
        }
      }}
      canGoBack={selectedSpace !== null}
    >
      <div className="flex-1 flex flex-col h-full overflow-hidden relative">
        <AnimatePresence mode="wait">
          {selectedSpace ? (
            /* Space Details Screen */
            <SpaceDetails
              key="space-details-view"
              space={selectedSpace}
              onBack={() => setSelectedSpace(null)}
            />
          ) : (
            /* Main Spaces Screen */
            <div key="spaces-list-view" className="flex-1 flex flex-col h-full overflow-hidden">
              {/* Minimalist Top Bar with Light/Dark Mode Toggle */}
              <TopAppBar
                onRefresh={handleManualRefresh}
                isLoading={isLoading}
                spaceCount={filteredSpaces.length}
                activeCount={activeSpacesCount}
                isPhoneFrame={isPhoneFrame}
                onTogglePhoneFrame={() => setIsPhoneFrame(!isPhoneFrame)}
                theme={theme}
                onToggleTheme={toggleTheme}
              />

              {/* Minimalist Search & Categories */}
              <SearchBar
                filter={filter}
                onChange={handleFilterUpdate}
                onOpenFilterSheet={() => setIsFilterSheetOpen(true)}
                activeFilterCount={activeFilterCount}
                totalSpaces={spaces.length}
                filteredCount={filteredSpaces.length}
              />

              {/* Minimalist Pagination Bar (Max 24 per page) */}
              <PaginationBar
                currentPage={isFilterActive ? frontendPage : currentPage}
                totalPages={isFilterActive ? totalFrontendPages : null}
                totalCount={filteredSpaces.length}
                pageSize={ITEMS_PER_PAGE}
                isLoading={isLoading}
                onPageChange={handlePageChange}
                isFilterActive={isFilterActive}
              />

              {/* Main Scrollable Content Area */}
              <div
                ref={scrollContainerRef}
                id="spaces-cards-scroll-container"
                className="flex-1 overflow-y-auto px-4 pb-16 pt-1 space-y-2.5 scrollbar-thin relative"
              >
                {/* Initial Loading State */}
                {isLoading && spaces.length === 0 ? (
                  <div className="py-20 flex flex-col items-center justify-center text-center space-y-2.5">
                    <div className="w-10 h-10 rounded-xl bg-slate-100 dark:bg-slate-800 flex items-center justify-center text-amber-500">
                      <Loader2 className="w-5 h-5 animate-spin" />
                    </div>
                    <p className="text-xs font-medium text-slate-600 dark:text-slate-400">
                      Loading spaces...
                    </p>
                  </div>
                ) : filteredSpaces.length === 0 ? (
                  <div className="py-16 text-center text-slate-500 dark:text-slate-400 space-y-3">
                    <div className="w-10 h-10 rounded-xl bg-slate-100 dark:bg-slate-800 flex items-center justify-center mx-auto text-slate-400">
                      <FilterX className="w-5 h-5" />
                    </div>
                    <h4 className="text-sm font-semibold text-slate-800 dark:text-slate-200">
                      No matching spaces
                    </h4>
                    <p className="text-xs text-slate-500 dark:text-slate-400 max-w-xs mx-auto">
                      Try adjusting your search query or reset your filters.
                    </p>
                    <div className="pt-2">
                      <button
                        id="btn-reset-filters-empty"
                        onClick={() => handleFilterUpdate({ ...INITIAL_FILTER, category: 'all' })}
                        className="px-4 py-2 rounded-xl bg-slate-900 dark:bg-white text-white dark:text-slate-950 text-xs font-semibold hover:opacity-90 transition-opacity cursor-pointer"
                      >
                        Reset filters
                      </button>
                    </div>
                  </div>
                ) : (
                  <>
                    {/* Render up to 24 spaces per page */}
                    {displayedSpaces.map((space) => (
                      <SpaceCard
                        key={space.id}
                        space={space}
                        onSelect={(s) => setSelectedSpace(s)}
                      />
                    ))}

                    {/* Bottom Pagination Controls when multiple pages exist */}
                    {((isFilterActive && totalFrontendPages > 1) || (!isFilterActive && spaces.length > 0)) && (
                      <div className="pt-2 pb-6 flex items-center justify-between gap-3 text-xs text-slate-600 dark:text-slate-400">
                        <button
                          onClick={() => handlePageChange((isFilterActive ? frontendPage : currentPage) - 1)}
                          disabled={(isFilterActive ? frontendPage : currentPage) === 0 || isLoading}
                          className="flex items-center gap-1 px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 hover:bg-slate-50 dark:hover:bg-slate-700 disabled:opacity-30 disabled:pointer-events-none transition-all cursor-pointer font-medium"
                        >
                          <ChevronLeft className="w-3.5 h-3.5" />
                          <span>Previous</span>
                        </button>

                        <span className="text-[11px] font-medium text-slate-500 dark:text-slate-400">
                          Page {(isFilterActive ? frontendPage : currentPage) + 1}
                          {isFilterActive && ` of ${totalFrontendPages}`}
                        </span>

                        <button
                          onClick={() => handlePageChange((isFilterActive ? frontendPage : currentPage) + 1)}
                          disabled={
                            isFilterActive
                              ? frontendPage >= totalFrontendPages - 1
                              : isLoading
                          }
                          className="flex items-center gap-1 px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 hover:bg-slate-50 dark:hover:bg-slate-700 disabled:opacity-30 disabled:pointer-events-none transition-all cursor-pointer font-medium"
                        >
                          <span>Next</span>
                          <ChevronRight className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    )}
                  </>
                )}
              </div>
            </div>
          )}
        </AnimatePresence>

        {/* Filter Bottom Sheet */}
        <FilterBottomSheet
          isOpen={isFilterSheetOpen}
          onClose={() => setIsFilterSheetOpen(false)}
          filter={filter}
          onChange={handleFilterUpdate}
          availableCategories={availableCategories}
          availableHardwares={availableHardwares}
          availableAuthors={availableAuthors}
          availableSdks={availableSdks}
          totalResults={filteredSpaces.length}
        />

        {/* Feedback Snackbar / Toast */}
        <Toast toast={toast} onDismiss={() => setToast(null)} />
      </div>
    </AndroidFrame>
  );
}

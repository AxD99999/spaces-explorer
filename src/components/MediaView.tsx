import React, { useState, useEffect } from 'react';
import {
  Film,
  Image as ImageIcon,
  FileText,
  Music,
  FileCode,
  Layers,
  ArrowLeft,
  Search,
  Download,
  Copy,
  Trash2,
  Check,
  ExternalLink,
  Sparkles,
  Maximize2,
  X,
  Play,
  Calendar,
  Compass,
} from 'lucide-react';
import { GeneratedMediaItem, GeneratedMediaType, HfSpace } from '../types';
import { getAllMediaItems, deleteMediaItem, clearAllMedia } from '../services/mediaStorage';

interface MediaViewProps {
  onBackToBrowse: () => void;
  onOpenSpaceById?: (spaceId: string) => void;
}

type MediaFilterTab = 'all' | 'image' | 'video' | 'text' | 'audio' | 'other';

export const MediaView: React.FC<MediaViewProps> = ({
  onBackToBrowse,
  onOpenSpaceById,
}) => {
  const [items, setItems] = useState<GeneratedMediaItem[]>([]);
  const [activeTab, setActiveTab] = useState<MediaFilterTab>('all');
  const [searchQuery, setSearchQuery] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [selectedItemForModal, setSelectedItemForModal] = useState<GeneratedMediaItem | null>(null);

  // Load items from IndexedDB
  const loadMedia = async () => {
    setIsLoading(true);
    try {
      const data = await getAllMediaItems();
      setItems(data);
    } catch (err) {
      console.error('Failed to load media items', err);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadMedia();
  }, []);

  const handleDelete = async (id: string, e?: React.MouseEvent) => {
    e?.stopPropagation();
    if (confirm('Delete this generated item permanently?')) {
      await deleteMediaItem(id);
      setItems((prev) => prev.filter((item) => item.id !== id));
      if (selectedItemForModal?.id === id) {
        setSelectedItemForModal(null);
      }
    }
  };

  const handleClearAll = async () => {
    if (items.length === 0) return;
    if (confirm('Are you sure you want to delete all saved media from device storage?')) {
      await clearAllMedia();
      setItems([]);
      setSelectedItemForModal(null);
    }
  };

  const handleCopy = (item: GeneratedMediaItem, e?: React.MouseEvent) => {
    e?.stopPropagation();
    const contentToCopy = item.textContent || item.url || '';
    if (contentToCopy) {
      navigator.clipboard.writeText(contentToCopy);
      setCopiedId(item.id);
      setTimeout(() => setCopiedId(null), 2000);
    }
  };

  const handleDownload = async (item: GeneratedMediaItem, e?: React.MouseEvent) => {
    e?.stopPropagation();
    if (item.type === 'text' && item.textContent) {
      const blob = new Blob([item.textContent], { type: 'text/plain;charset=utf-8' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${item.title.replace(/[^a-zA-Z0-9_-]/g, '_')}.txt`;
      a.click();
      URL.revokeObjectURL(url);
      return;
    }

    if (item.url) {
      try {
        const res = await fetch(item.url);
        const blob = await res.blob();
        const blobUrl = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = blobUrl;
        const ext = item.type === 'video' ? 'mp4' : item.type === 'audio' ? 'mp3' : 'png';
        a.download = `${item.title.replace(/[^a-zA-Z0-9_-]/g, '_')}.${ext}`;
        a.click();
        URL.revokeObjectURL(blobUrl);
      } catch {
        // Fallback standard download
        const a = document.createElement('a');
        a.href = item.url;
        a.download = item.title;
        a.target = '_blank';
        a.click();
      }
    }
  };

  // Filter items
  const filteredItems = items.filter((item) => {
    if (activeTab !== 'all' && item.type !== activeTab) {
      return false;
    }
    if (!searchQuery.trim()) return true;
    const query = searchQuery.toLowerCase();
    const matchTitle = (item.title || '').toLowerCase().includes(query);
    const matchPrompt = (item.prompt || '').toLowerCase().includes(query);
    const matchSpace = (item.spaceTitle || item.spaceId || '').toLowerCase().includes(query);
    const matchText = (item.textContent || '').toLowerCase().includes(query);
    return matchTitle || matchPrompt || matchSpace || matchText;
  });

  const tabCounts = {
    all: items.length,
    image: items.filter((i) => i.type === 'image').length,
    video: items.filter((i) => i.type === 'video').length,
    text: items.filter((i) => i.type === 'text').length,
    audio: items.filter((i) => i.type === 'audio').length,
    other: items.filter((i) => i.type === 'other').length,
  };

  return (
    <div className="flex-1 flex flex-col h-full bg-slate-50 dark:bg-slate-950 overflow-hidden">
      {/* Top Header */}
      <header className="px-4 py-3 bg-white dark:bg-slate-900 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between shrink-0 shadow-xs">
        <div className="flex items-center gap-2.5">
          <button
            onClick={onBackToBrowse}
            className="p-1.5 -ml-1 rounded-lg text-slate-500 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors cursor-pointer"
            aria-label="Back to browse"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <div>
            <h2 className="text-sm font-semibold text-slate-900 dark:text-white flex items-center gap-2">
              <Film className="w-4 h-4 text-amber-500" />
              Generated Media
              <span className="text-[11px] font-mono text-slate-400 font-normal">
                ({items.length})
              </span>
            </h2>
            <p className="text-[11px] text-slate-500 dark:text-slate-400">
              Permanently saved to device storage
            </p>
          </div>
        </div>

        {items.length > 0 && (
          <button
            onClick={handleClearAll}
            className="flex items-center gap-1 px-2.5 py-1 text-[11px] text-rose-600 dark:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-950/30 rounded-lg transition-colors cursor-pointer font-medium"
          >
            <Trash2 className="w-3 h-3" />
            <span>Clear All</span>
          </button>
        )}
      </header>

      {/* Filter Toggles Bar */}
      <div className="px-4 py-2 bg-white/70 dark:bg-slate-900/70 border-b border-slate-200/70 dark:border-slate-800/70 flex items-center justify-between gap-3 overflow-x-auto">
        <div className="flex items-center gap-1.5 overflow-x-auto py-0.5">
          {(
            [
              { id: 'all', label: 'All', icon: Layers },
              { id: 'image', label: 'Images', icon: ImageIcon },
              { id: 'video', label: 'Videos', icon: Film },
              { id: 'text', label: 'Text', icon: FileText },
              { id: 'audio', label: 'Audio', icon: Music },
              { id: 'other', label: 'Other', icon: FileCode },
            ] as const
          ).map((tab) => {
            const Icon = tab.icon;
            const count = tabCounts[tab.id];
            const isActive = activeTab === tab.id;

            return (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-medium whitespace-nowrap transition-all cursor-pointer ${
                  isActive
                    ? 'bg-amber-500 text-slate-950 font-semibold shadow-xs'
                    : 'bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white'
                }`}
              >
                <Icon className="w-3.5 h-3.5" />
                <span>{tab.label}</span>
                {count > 0 && (
                  <span
                    className={`text-[10px] px-1.5 py-0.2 rounded-full font-mono ${
                      isActive
                        ? 'bg-slate-950/20 text-slate-950 font-bold'
                        : 'bg-slate-200 dark:bg-slate-700 text-slate-500 dark:text-slate-400'
                    }`}
                  >
                    {count}
                  </span>
                )}
              </button>
            );
          })}
        </div>

        {items.length > 0 && (
          <div className="relative w-40 sm:w-56 shrink-0 hidden sm:block">
            <Search className="w-3.5 h-3.5 absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              placeholder="Search media..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-8 pr-3 py-1.5 text-xs rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 text-slate-900 dark:text-slate-100 placeholder-slate-400 focus:outline-hidden focus:border-amber-500"
            />
          </div>
        )}
      </div>

      {/* Main Content Grid */}
      <div className="flex-1 overflow-y-auto p-4 max-w-6xl mx-auto w-full">
        {isLoading ? (
          <div className="py-20 text-center text-xs text-slate-500">
            Loading saved media from device...
          </div>
        ) : items.length === 0 ? (
          <div className="py-20 text-center space-y-3 max-w-sm mx-auto">
            <div className="w-12 h-12 rounded-2xl bg-amber-500/10 text-amber-500 flex items-center justify-center mx-auto">
              <Film className="w-6 h-6" />
            </div>
            <h3 className="text-sm font-semibold text-slate-800 dark:text-slate-200">
              No Generated Media Yet
            </h3>
            <p className="text-xs text-slate-500 dark:text-slate-400 leading-relaxed">
              Every image, video, audio clip, or text you generate in any space will automatically be saved here permanently.
            </p>
            <div className="pt-2">
              <button
                onClick={onBackToBrowse}
                className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-slate-900 dark:bg-white text-white dark:text-slate-950 text-xs font-semibold hover:opacity-90 transition-opacity cursor-pointer shadow-xs"
              >
                <Compass className="w-4 h-4" />
                <span>Start Generating</span>
              </button>
            </div>
          </div>
        ) : filteredItems.length === 0 ? (
          <div className="py-16 text-center text-xs text-slate-500">
            No {activeTab === 'all' ? '' : activeTab} media found matching your filter.
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3.5">
            {filteredItems.map((item) => (
              <div
                key={item.id}
                onClick={() => setSelectedItemForModal(item)}
                className="group relative rounded-2xl bg-white dark:bg-slate-900 border border-slate-200/90 dark:border-slate-800 shadow-2xs hover:shadow-md transition-all cursor-pointer flex flex-col justify-between overflow-hidden"
              >
                {/* Media Preview Thumbnail */}
                <div className="relative aspect-video bg-slate-950 flex items-center justify-center overflow-hidden">
                  {item.type === 'image' && item.url ? (
                    <img
                      src={item.url}
                      alt={item.title}
                      className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                      loading="lazy"
                    />
                  ) : item.type === 'video' && item.url ? (
                    <video
                      src={item.url}
                      className="w-full h-full object-cover"
                      muted
                      playsInline
                    />
                  ) : item.type === 'audio' ? (
                    <div className="flex flex-col items-center justify-center gap-2 p-4 text-slate-400">
                      <Music className="w-8 h-8 text-amber-500" />
                      <span className="text-[11px] font-mono">Audio Clip</span>
                    </div>
                  ) : item.type === 'text' ? (
                    <div className="p-3.5 w-full h-full text-slate-300 font-mono text-[10px] overflow-hidden select-none bg-slate-900">
                      <p className="line-clamp-4 leading-relaxed">{item.textContent}</p>
                    </div>
                  ) : (
                    <div className="flex flex-col items-center justify-center gap-2 p-4 text-slate-400">
                      <FileCode className="w-8 h-8 text-sky-500" />
                      <span className="text-[11px] font-mono">JSON / Object</span>
                    </div>
                  )}

                  {/* Type Badge */}
                  <span className="absolute top-2 left-2 px-2 py-0.5 rounded-md text-[9px] font-semibold uppercase tracking-wider bg-black/60 backdrop-blur-xs text-white border border-white/10">
                    {item.type}
                  </span>

                  {/* Hover Quick Actions */}
                  <div className="absolute top-2 right-2 flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                    <button
                      onClick={(e) => handleDownload(item, e)}
                      className="p-1.5 rounded-lg bg-black/70 text-white hover:bg-black transition-colors"
                      title="Download"
                    >
                      <Download className="w-3.5 h-3.5" />
                    </button>
                    <button
                      onClick={(e) => handleCopy(item, e)}
                      className="p-1.5 rounded-lg bg-black/70 text-white hover:bg-black transition-colors"
                      title="Copy"
                    >
                      {copiedId === item.id ? (
                        <Check className="w-3.5 h-3.5 text-emerald-400" />
                      ) : (
                        <Copy className="w-3.5 h-3.5" />
                      )}
                    </button>
                    <button
                      onClick={(e) => handleDelete(item.id, e)}
                      className="p-1.5 rounded-lg bg-black/70 text-white hover:bg-rose-600 transition-colors"
                      title="Delete"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>

                {/* Metadata details */}
                <div className="p-3 space-y-1.5 flex-1 flex flex-col justify-between">
                  <div>
                    <div className="flex items-center justify-between text-[11px] text-slate-500 dark:text-slate-400">
                      <span className="flex items-center gap-1 truncate font-medium text-slate-700 dark:text-slate-300">
                        <span>{item.spaceEmoji || '🤗'}</span>
                        <span className="truncate">{item.spaceTitle}</span>
                      </span>
                      <span className="text-[10px] font-mono shrink-0">
                        {new Date(item.createdAt).toLocaleDateString(undefined, {
                          month: 'short',
                          day: 'numeric',
                        })}
                      </span>
                    </div>

                    {item.prompt && (
                      <p className="text-xs text-slate-600 dark:text-slate-300 line-clamp-2 mt-1 leading-snug">
                        {item.prompt}
                      </p>
                    )}
                  </div>

                  {/* Footer Actions */}
                  <div className="pt-2 border-t border-slate-100 dark:border-slate-800 flex items-center justify-between text-xs">
                    <button
                      onClick={(e) => handleCopy(item, e)}
                      className="inline-flex items-center gap-1 text-[11px] text-slate-500 hover:text-slate-900 dark:hover:text-white cursor-pointer"
                    >
                      {copiedId === item.id ? (
                        <span className="text-emerald-500 font-medium">Copied</span>
                      ) : (
                        <>
                          <Copy className="w-3 h-3" />
                          <span>Copy</span>
                        </>
                      )}
                    </button>

                    <div className="flex items-center gap-2">
                      <button
                        onClick={(e) => handleDownload(item, e)}
                        className="inline-flex items-center gap-1 text-[11px] text-slate-500 hover:text-slate-900 dark:hover:text-white cursor-pointer"
                      >
                        <Download className="w-3 h-3" />
                        <span>Save</span>
                      </button>

                      <button
                        onClick={(e) => handleDelete(item.id, e)}
                        className="p-1 text-slate-400 hover:text-rose-600 cursor-pointer"
                        title="Delete item"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Media Detail Modal */}
      {selectedItemForModal && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm"
          onClick={() => setSelectedItemForModal(null)}
        >
          <div
            className="relative w-full max-w-2xl bg-white dark:bg-slate-900 rounded-2xl overflow-hidden shadow-2xl border border-slate-200 dark:border-slate-800 max-h-[90vh] flex flex-col"
            onClick={(e) => e.stopPropagation()}
          >
            {/* Modal Header */}
            <div className="p-3.5 border-b border-slate-100 dark:border-slate-800 flex items-center justify-between">
              <div className="min-w-0 flex items-center gap-2">
                <span className="text-base">{selectedItemForModal.spaceEmoji || '🤗'}</span>
                <div>
                  <h3 className="text-xs font-semibold text-slate-900 dark:text-white truncate">
                    {selectedItemForModal.title}
                  </h3>
                  <p className="text-[10px] text-slate-400 font-mono">
                    From {selectedItemForModal.spaceTitle} &bull; {new Date(selectedItemForModal.createdAt).toLocaleString()}
                  </p>
                </div>
              </div>

              <button
                onClick={() => setSelectedItemForModal(null)}
                className="p-1.5 rounded-lg text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {/* Modal Body */}
            <div className="flex-1 overflow-y-auto p-4 flex flex-col items-center justify-center bg-slate-950">
              {selectedItemForModal.type === 'image' && selectedItemForModal.url ? (
                <img
                  src={selectedItemForModal.url}
                  alt={selectedItemForModal.title}
                  className="max-h-[60vh] max-w-full object-contain rounded-xl"
                />
              ) : selectedItemForModal.type === 'video' && selectedItemForModal.url ? (
                <video
                  src={selectedItemForModal.url}
                  controls
                  autoPlay
                  className="max-h-[60vh] max-w-full rounded-xl"
                />
              ) : selectedItemForModal.type === 'audio' && selectedItemForModal.url ? (
                <div className="p-8 w-full max-w-md">
                  <audio src={selectedItemForModal.url} controls className="w-full" />
                </div>
              ) : selectedItemForModal.textContent ? (
                <div className="p-4 w-full text-slate-200 font-mono text-xs whitespace-pre-wrap max-h-[60vh] overflow-y-auto bg-slate-900 rounded-xl">
                  {selectedItemForModal.textContent}
                </div>
              ) : null}
            </div>

            {/* Modal Footer with Actions */}
            <div className="p-3.5 bg-slate-50 dark:bg-slate-900 border-t border-slate-100 dark:border-slate-800 flex items-center justify-between gap-3">
              {selectedItemForModal.prompt && (
                <p className="text-[11px] text-slate-500 dark:text-slate-400 truncate flex-1">
                  <strong>Prompt:</strong> {selectedItemForModal.prompt}
                </p>
              )}

              <div className="flex items-center gap-2 shrink-0 ml-auto">
                <button
                  onClick={() => handleCopy(selectedItemForModal)}
                  className="inline-flex items-center gap-1 px-3 py-1.5 rounded-xl border border-slate-200 dark:border-slate-700 text-xs font-medium text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
                >
                  {copiedId === selectedItemForModal.id ? (
                    <Check className="w-3.5 h-3.5 text-emerald-500" />
                  ) : (
                    <Copy className="w-3.5 h-3.5" />
                  )}
                  <span>{copiedId === selectedItemForModal.id ? 'Copied' : 'Copy'}</span>
                </button>

                <button
                  onClick={() => handleDownload(selectedItemForModal)}
                  className="inline-flex items-center gap-1 px-3 py-1.5 rounded-xl bg-amber-500 hover:bg-amber-600 text-slate-950 text-xs font-semibold transition-colors shadow-2xs"
                >
                  <Download className="w-3.5 h-3.5" />
                  <span>Download</span>
                </button>

                <button
                  onClick={() => handleDelete(selectedItemForModal.id)}
                  className="p-1.5 rounded-xl text-rose-500 hover:bg-rose-50 dark:hover:bg-rose-950/30 transition-colors"
                  title="Delete"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

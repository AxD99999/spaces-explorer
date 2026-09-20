export interface HfAuthorData {
  _id?: string;
  avatarUrl?: string;
  fullname?: string;
  name?: string;
  type?: string;
  isPro?: boolean;
  isHf?: boolean;
  isHfAdmin?: boolean;
  isMod?: boolean;
  followerCount?: number;
  isUserFollowing?: boolean;
}

export interface HfSpaceHardware {
  current?: string | null;
  requested?: string | null;
}

export interface HfSpaceDomain {
  domain: string;
  stage: string;
}

export interface HfSpaceRuntime {
  stage?: 'RUNNING' | 'SLEEPING' | 'PAUSED' | 'STOPPED' | 'BUILDING' | string;
  hardware?: HfSpaceHardware;
  gcTimeout?: number;
  replicas?: {
    current?: number;
    requested?: number;
  };
  devMode?: boolean;
  domains?: HfSpaceDomain[];
  sha?: string;
}

export interface HfSpace {
  id: string;
  title?: string;
  author: string;
  authorData?: HfAuthorData;
  colorFrom?: string;
  colorTo?: string;
  emoji?: string;
  sdk?: 'gradio' | 'docker' | 'static' | 'streamlit' | string;
  runtime?: HfSpaceRuntime;
  shortDescription?: string;
  ai_short_description?: string;
  ai_category?: string;
  likes?: number;
  trendingScore?: number;
  createdAt?: string;
  lastModified?: string;
  tags?: string[];
  pinned?: boolean;
  featured?: boolean;
  visibility?: string;
  disabled?: boolean;
  subdomain?: string;
  repoType?: string;
  isLikedByUser?: boolean;
}

export interface HfSpacesApiResponse {
  spaces: HfSpace[];
  numItemsPerPage?: number;
  numTotalItems?: number | null;
  pageIndex?: number;
}

export interface SemanticCategory {
  id: string;
  name: string;
  emoji: string;
}

export const HF_SEMANTIC_CATEGORIES: SemanticCategory[] = [
  { id: 'image-generation', name: 'Image Generation', emoji: '🎨' },
  { id: 'text-generation', name: 'Text Generation', emoji: '✍️' },
  { id: 'video-generation', name: 'Video Generation', emoji: '🎥' },
  { id: 'chatbots', name: 'Chatbots', emoji: '💬' },
  { id: 'image-editing', name: 'Image Editing', emoji: '🪄' },
  { id: 'music-generation', name: 'Music Generation', emoji: '🎵' },
  { id: 'speech-synthesis', name: 'Speech Synthesis', emoji: '🎙️' },
  { id: 'text-analysis', name: 'Text Analysis', emoji: '📊' },
  { id: 'data-visualization', name: 'Data Visualization', emoji: '📈' },
  { id: 'ocr', name: 'OCR & Documents', emoji: '📄' },
];

export interface SpaceFilterState {
  search: string;
  status: 'all' | 'running' | 'sleeping' | 'other';
  sdk: string; // 'all' or 'gradio' | 'docker' | 'static' | 'streamlit'
  category: string; // 'all' or semantic category id (e.g., 'image-generation')
  hardware: string; // 'all' or specific
  author: string; // 'all' or specific
  pinnedOnly: boolean;
  featuredOnly: boolean;
  sortBy: 'trending-desc' | 'likes-desc' | 'name-asc' | 'name-desc' | 'recent-desc' | 'created-desc';
}

export interface UserApiKey {
  id: string;
  name: string;
  token: string;
  createdAt: string;
  lastUsed?: string;
  isDefault?: boolean;
}

export interface SpaceEndpointParam {
  label: string;
  parameter_name: string;
  parameter_has_default?: boolean;
  parameter_default?: any;
  type?: any;
  python_type?: {
    type: string;
    description?: string;
  };
  component?: string;
  example_input?: any;
}

export interface SpaceEndpointReturn {
  label: string;
  type?: any;
  python_type?: {
    type: string;
    description?: string;
  };
  component?: string;
}

export interface SpaceEndpointSpec {
  name: string; // e.g., '/predict', '/generate'
  parameters: SpaceEndpointParam[];
  returns: SpaceEndpointReturn[];
}

export interface SpaceApiSpec {
  spaceId: string;
  subdomain?: string;
  host: string;
  endpoints: SpaceEndpointSpec[];
  raw?: any;
  sdk?: string;
}

export type AppNavView = 'browse' | 'currently-running' | 'media' | 'favourites' | 'api-keys';

export type GeneratedMediaType = 'image' | 'video' | 'audio' | 'text' | 'other';

export interface GeneratedMediaItem {
  id: string;
  type: GeneratedMediaType;
  url?: string;
  textContent?: string;
  title: string;
  prompt?: string;
  spaceId: string;
  spaceTitle: string;
  spaceEmoji?: string;
  createdAt: string;
  metadata?: {
    endpoint?: string;
    elapsedSeconds?: number;
    mimeType?: string;
    size?: number;
  };
}

export interface ActiveSpaceTask {
  id: string;
  space: HfSpace;
  endpoint: string;
  formValues: Record<string, any>;
  imagePreviews?: Record<string, string>;
  multiImagePreviews?: Record<string, string[]>;
  status: 'running' | 'completed' | 'error';
  startTime: number;
  elapsedSeconds: number;
  result?: any;
  error?: string | null;
  savedMediaIds?: string[];
}

export interface ToastMessage {
  id: string;
  message: string;
  actionLabel?: string;
  onAction?: () => void;
  type?: 'info' | 'success' | 'warning' | 'error';
}


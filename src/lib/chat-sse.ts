export interface VisualThumb {
  imageUrl?: string;
  slotLabel?: string;
  scorePercent?: number;
  source?: string;
  sourceId?: string;
  slot?: string;
}

export interface ChatSource {
  id?: string;
  recordId?: string;
  chunkId?: string;
  source?: string;
  title?: string;
  excerpt?: string;
  content?: string;
  score?: number;
  scorePercent?: number;
  imageUrl?: string;
  sourceLabel?: string;
  visualSource?: string;
  goodsNo?: string;
  productName?: string;
  sampler?: string;
  albumName?: string;
  slotLabel?: string;
  empty?: boolean;
  /** SAME_PRODUCT / SIMILAR_REFERENCE / MIXED。历史记录靠它选卡片。 */
  scenario?: string;
  /** product 是同款一张卡，image 是单张图。 */
  cardType?: string;
  images?: VisualThumb[];
  filters?: VisualFilter[];
  filterNotice?: string;
  /** 已核对的打样单。没有记录时不出现。 */
  sampleOrderPath?: string;
  /** 已核对的商品详情。没有记录时不出现。 */
  productDetailPath?: string;
}

export interface VisualFilter {
  id: string;
  kind?: string;
  label: string;
  value?: string;
  applied?: boolean;
  relaxed?: boolean;
}

export function isProductCard(hit: { cardType?: string } | null | undefined): boolean {
  return hit?.cardType === 'product';
}

const CITE_ID = /^[A-Za-z]\d+$/;

/** [[E1]] 这类编号。相似图的 visual-1 不进引用按钮。 */
export function citeIdsFromSources(sources?: ChatSource[]): string[] {
  return (sources || [])
    .filter(source => source?.source !== 'visual_match')
    .map(source => source?.id)
    .filter((id): id is string => !!id && CITE_ID.test(id));
}

export function visualMatchesFromSources(sources?: ChatSource[]): ChatSource[] {
  return (sources || []).filter(source => source?.source === 'visual_match');
}

export interface ChatSseEvent {
  type?: string;
  content?: string;
  sources?: ChatSource[];
  historyId?: string;
  conversationId?: string;
  images?: unknown;
  [key: string]: unknown;
}

export interface ParsedChatAnswer {
  content: string;
  sources: ChatSource[];
  /** goods_library 事件原文。来源帧被丢掉时，照片仍在这里。 */
  goodsLibrary: unknown[];
  historyId: string;
  conversationId: string;
  isStreaming: boolean;
  sawTerminal: boolean;
}

/**
 * 按 SSE 事件分帧，不按单个 data: 行当 JSON。
 * Spring 的格式是 event:message\\ndata:{json}\\n\\n。
 * 多行 data: 按规范用换行拼回；流结束时最后一帧可以没有空行。
 */
export function takeSseEvents(buffer: string, flush: boolean): { events: ChatSseEvent[]; rest: string } {
  const normalized = buffer.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
  const blocks: string[] = [];
  let rest = normalized;
  let splitAt = rest.indexOf('\n\n');
  while (splitAt >= 0) {
    blocks.push(rest.slice(0, splitAt));
    rest = rest.slice(splitAt + 2);
    splitAt = rest.indexOf('\n\n');
  }
  if (flush && rest.trim()) {
    blocks.push(rest);
    rest = '';
  }
  const events: ChatSseEvent[] = [];
  for (const block of blocks) {
    const event = parseSseBlock(block);
    if (event) events.push(event);
  }
  return { events, rest };
}

export function parseChatSse(text: string, flush = true): ParsedChatAnswer {
  const { events } = takeSseEvents(text, flush);
  const state: ParsedChatAnswer = {
    content: '',
    sources: [],
    goodsLibrary: [],
    historyId: '',
    conversationId: '',
    isStreaming: true,
    sawTerminal: false,
  };
  for (const event of events) applyChatSseEvent(state, event);
  if (flush && state.isStreaming) state.isStreaming = false;
  return state;
}

export function applyChatSseEvent(state: ParsedChatAnswer, event: ChatSseEvent) {
  if (event.type === 'sources' && Array.isArray(event.sources)) {
    state.sources = event.sources;
    return;
  }
  if (event.type === 'goods_library' && Array.isArray(event.entries)) {
    state.goodsLibrary = event.entries;
    return;
  }
  if (event.type === 'content' && typeof event.content === 'string') {
    state.content += event.content;
    return;
  }
  if (event.type === 'conversation') {
    const id = typeof event.conversationId === 'string' ? event.conversationId : '';
    if (id) state.conversationId = id;
    return;
  }
  if (event.type === 'done') {
    state.sawTerminal = true;
    state.isStreaming = false;
    if (typeof event.historyId === 'string' && event.historyId) state.historyId = event.historyId;
    if (typeof event.conversationId === 'string' && event.conversationId) state.conversationId = event.conversationId;
    if (Array.isArray(event.sources)) state.sources = event.sources;
    return;
  }
  if (event.type === 'error') {
    state.sawTerminal = true;
    state.isStreaming = false;
    if (!state.content && typeof event.content === 'string') state.content = event.content;
  }
}

export function mapHistoryChatMessage(raw: {
  id?: string;
  role?: string;
  content?: string;
  reasoning?: string;
  reasoning_content?: string;
  sources?: ChatSource[];
}) {
  return {
    role: raw.role === 'user' ? 'user' as const : 'assistant' as const,
    content: raw.content || '',
    reasoning: raw.reasoning || raw.reasoning_content || undefined,
    historyId: raw.id || '',
    sources: Array.isArray(raw.sources) ? raw.sources : [],
    isStreaming: false as const,
  };
}

function parseSseBlock(block: string): ChatSseEvent | null {
  const dataLines: string[] = [];
  let eventName = '';
  for (const rawLine of block.split('\n')) {
    const line = rawLine.trimEnd();
    if (!line || line.startsWith(':')) continue;
    const colon = line.indexOf(':');
    const field = colon === -1 ? line : line.slice(0, colon);
    let value = colon === -1 ? '' : line.slice(colon + 1);
    if (value.startsWith(' ')) value = value.slice(1);
    if (field === 'data') dataLines.push(value);
    else if (field === 'event') eventName = value.trim();
  }
  if (dataLines.length === 0) return null;
  const data = dataLines.join('\n').trim();
  if (!data || data === '[DONE]') return null;
  try {
    const parsed = JSON.parse(data) as ChatSseEvent;
    if (!parsed || typeof parsed !== 'object') return null;
    if (!parsed.type && eventName === 'conversation') {
      return { type: 'conversation', conversationId: data.replace(/^"|"$/g, '') };
    }
    return parsed;
  } catch {
    if (eventName === 'conversation') {
      return { type: 'conversation', conversationId: data.replace(/^"|"$/g, '') };
    }
    return null;
  }
}

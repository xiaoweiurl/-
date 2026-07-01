'use client';

import { useState, useEffect, useCallback } from 'react';
import {
  Database, Plus, Trash2, Edit3, Save, X, ChevronRight,
  Settings, Table2, FileText, Search, RefreshCw, ArrowLeft,
  ToggleLeft, ToggleRight, GripVertical, Eye, EyeOff, Copy
} from 'lucide-react';

/* ===== 类型 ===== */
interface FieldDef {
  id?: string;
  name: string;
  label: string;
  type: 'text' | 'number' | 'date' | 'select' | 'textarea' | 'boolean' | 'url' | 'email' | 'decimal' | 'multi_select';
  required: boolean;
  defaultValue?: string;
  options?: string; // 逗号分隔的选项(select用)
  orderNum: number;
  visible: boolean;
  searchable: boolean;
}

interface DataModel {
  id: string;
  name: string;
  description?: string;
  icon?: string;
  color?: string;
  fields?: FieldDef[];
  recordCount?: number;
  createdAt: string;
  updatedAt: string;
}

interface DataRecord {
  id: string;
  modelId: string;
  data: Record<string, unknown>;
  createdBy?: string;
  createdAt: string;
  updatedAt: string;
}

type Tab = 'models' | 'fields' | 'records' | 'detail';

const FIELD_TYPES: FieldDef['type'][] = ['text', 'number', 'decimal', 'date', 'select', 'multi_select', 'textarea', 'boolean', 'url', 'email'];
const TYPE_LABELS: Record<string, string> = {
  text: '文本', number: '整数', decimal: '小数', date: '日期', select: '下拉选择',
  multi_select: '多选', textarea: '长文本', boolean: '布尔', url: '链接', email: '邮箱'
};
const MODEL_COLORS = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4', '#ec4899', '#84cc16'];

/* ===== API ===== */
const API = '/api/data-models';
async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API}${path}`, { headers: { 'Content-Type': 'application/json' }, ...init });
  const json = await res.json();
  return json.data ?? json;
}

/** 将后端 DB 列名映射为前端 FieldDef 格式 */
function mapFieldFromBackend(raw: Record<string, unknown>): FieldDef {
  return {
    id: String(raw.id ?? ''),
    name: String(raw.code ?? raw.name ?? ''),           // code=英文标识 → name
    label: String(raw.name ?? raw.code ?? ''),           // name=中文显示名 → label
    type: (raw.field_type ?? raw.type ?? 'text') as FieldDef['type'],
    required: Boolean(raw.required),
    defaultValue: raw.default_value != null ? String(raw.default_value) : undefined,
    options: raw.options != null ? String(raw.options) : undefined,
    orderNum: Number(raw.sort_order ?? raw.orderNum ?? 0),
    visible: Boolean(raw.show_in_list ?? raw.visible ?? true),
    searchable: Boolean(raw.searchable),
  };
}

/** 将前端 FieldDef 映射为后端字段格式 */
function mapFieldToBackend(f: FieldDef): Record<string, unknown> {
  return {
    name: f.label,             // 后端 name = 中文显示名
    code: f.name,              // 后端 code = 英文标识
    fieldType: f.type,
    required: f.required,
    showInList: f.visible,
    searchable: f.searchable,
    sortOrder: f.orderNum,
    defaultValue: f.defaultValue ?? null,
    options: f.options ?? null,
  };
}

/* ===== 主页面 ===== */
export default function DataModelsPage() {
  const [tab, setTab] = useState<Tab>('models');
  const [models, setModels] = useState<DataModel[]>([]);
  const [selectedModel, setSelectedModel] = useState<DataModel | null>(null);
  const [records, setRecords] = useState<DataRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');

  const fetchModels = useCallback(async () => {
    setLoading(true);
    try {
      const list = await api<unknown[]>('');
      if (Array.isArray(list)) {
        // 后端 listModels 返回 fieldCount 而非 fields，做适配
        const adapted: DataModel[] = list.map((item: unknown) => {
          const r = item as Record<string, unknown>;
          return {
            id: String(r.id ?? ''),
            name: String(r.name ?? ''),
            description: r.description != null ? String(r.description) : undefined,
            icon: r.icon != null ? String(r.icon) : undefined,
            color: r.color != null ? String(r.color) : undefined,
            fields: [], // 列表不加载字段，选中时再获取
            recordCount: r.recordCount != null ? Number(r.recordCount) : (r.record_count != null ? Number(r.record_count) : 0),
            createdAt: String(r.created_at ?? r.createdAt ?? ''),
            updatedAt: String(r.updated_at ?? r.updatedAt ?? ''),
          };
        });
        setModels(adapted);
      } else {
        setModels([]);
      }
    } catch { setModels([]); }
    setLoading(false);
  }, []);

  const fetchRecords = useCallback(async (modelId: string) => {
    try {
      const list = await api<DataRecord[]>(`/${modelId}/records`);
      setRecords(Array.isArray(list) ? list : []);
    } catch { setRecords([]); }
  }, []);

  useEffect(() => { fetchModels(); }, [fetchModels]);

  const selectModel = async (m: DataModel) => {
    // 从列表模型构建基础模型（含 recordCount）
    const baseModel: DataModel = { ...m, fields: [] };
    setSelectedModel(baseModel);
    setTab('fields');
    fetchRecords(m.id);
    // 获取模型详情（含字段列表）
    try {
      const detail = await api<Record<string, unknown>>(`/${m.id}`);
      const rawFields = detail.fields ?? [];
      const fields: FieldDef[] = Array.isArray(rawFields)
        ? rawFields.map((f: unknown) => mapFieldFromBackend(f as Record<string, unknown>))
        : [];
      setSelectedModel({ ...baseModel, fields, recordCount: detail.recordCount != null ? Number(detail.recordCount) : m.recordCount });
    } catch {
      // 如果获取详情失败，尝试通过 /fields 接口获取
      try {
        const rawFields = await api<unknown[]>(`/${m.id}/fields`);
        const fields: FieldDef[] = Array.isArray(rawFields)
          ? rawFields.map((f: unknown) => mapFieldFromBackend(f as Record<string, unknown>))
          : [];
        setSelectedModel({ ...baseModel, fields });
      } catch { /* 保持无字段状态 */ }
    }
  };

  const deleteModel = async (id: string) => {
    if (!confirm('确定删除此数据模型？所有字段和记录将被删除。')) return;
    await api(`/${id}`, { method: 'DELETE' });
    fetchModels();
    if (selectedModel?.id === id) { setSelectedModel(null); setTab('models'); }
  };

  const filtered = models.filter(m => m.name.toLowerCase().includes(search.toLowerCase()));

  return (
    <div className="min-h-screen bg-[#0a0e1a] text-slate-100">
      {/* 顶栏 */}
      <div className="sticky top-0 z-30 bg-[#0f172a]/90 backdrop-blur-xl border-b border-blue-500/20 px-6 py-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Database className="w-6 h-6 text-blue-400" />
            <div>
              <h1 className="text-xl font-bold">数据模型</h1>
              <p className="text-xs text-slate-400">元数据驱动 · 动态字段 · 自定义表单 · 可配置数据模型</p>
            </div>
          </div>
          <div className="flex items-center gap-3">
            {tab !== 'models' && (
              <button onClick={() => { setTab('models'); setSelectedModel(null); }}
                className="flex items-center gap-1 px-3 py-1.5 rounded-lg bg-slate-700 hover:bg-slate-600 text-sm transition">
                <ArrowLeft className="w-4 h-4" />返回列表
              </button>
            )}
            <button onClick={fetchModels} className="p-2 rounded-lg bg-slate-700 hover:bg-slate-600 transition">
              <RefreshCw className="w-4 h-4" />
            </button>
          </div>
        </div>
      </div>

      <div className="max-w-7xl mx-auto p-6">
        {/* Tab导航 */}
        {selectedModel && (
          <div className="flex gap-1 mb-6 bg-slate-800/50 rounded-xl p-1">
            {([['fields', '字段配置', Settings], ['records', '数据记录', Table2], ['detail', '模型详情', FileText]] as const).map(([key, label, Icon]) => (
              <button key={key} onClick={() => setTab(key as Tab)}
                className={`flex-1 flex items-center justify-center gap-2 px-4 py-2.5 rounded-lg text-sm font-medium transition ${tab === key ? 'bg-blue-600 text-white shadow-lg shadow-blue-600/20' : 'text-slate-400 hover:text-slate-200 hover:bg-slate-700/50'}`}>
                <Icon className="w-4 h-4" />{label}
              </button>
            ))}
          </div>
        )}

        {/* 内容区 */}
        {tab === 'models' && (
          <ModelsList models={filtered} loading={loading} search={search} setSearch={setSearch}
            onSelect={selectModel} onDelete={deleteModel} onRefresh={fetchModels} />
        )}
        {tab === 'fields' && selectedModel && (
          <FieldsEditor model={selectedModel} onSave={async (m) => {
            // 更新模型基础信息
            await api(`/${m.id}`, { method: 'PUT', body: JSON.stringify({ name: m.name, description: m.description, icon: m.icon, color: m.color }) });
            // 同步字段：逐条更新/新增
            const existingFields = selectedModel.fields ?? [];
            const currentFields = m.fields ?? [];
            const existingIds = new Set(existingFields.map(f => f.id));
            for (const f of currentFields) {
              const backendField = mapFieldToBackend(f);
              if (f.id && !f.id.startsWith('field_') && existingIds.has(f.id)) {
                // 更新已有字段
                await api(`/${m.id}/fields/${f.id}`, { method: 'PUT', body: JSON.stringify(backendField) });
              } else {
                // 新增字段
                await api(`/${m.id}/fields`, { method: 'POST', body: JSON.stringify(backendField) });
              }
            }
            // 删除已移除的字段
            const newIds = new Set(currentFields.map(f => f.id));
            for (const f of existingFields) {
              if (f.id && !f.id.startsWith('field_') && !newIds.has(f.id)) {
                await api(`/${m.id}/fields/${f.id}`, { method: 'DELETE' });
              }
            }
            // 重新获取详情
            fetchModels();
            try {
              const detail = await api<Record<string, unknown>>(`/${m.id}`);
              const rawFields = detail.fields ?? [];
              const fields: FieldDef[] = Array.isArray(rawFields) ? rawFields.map((rf: unknown) => mapFieldFromBackend(rf as Record<string, unknown>)) : [];
              setSelectedModel({ ...m, fields });
            } catch { setSelectedModel(m); }
          }} />
        )}
        {tab === 'records' && selectedModel && (
          <RecordsView model={selectedModel} records={records}
            onRefresh={() => fetchRecords(selectedModel.id)} />
        )}
        {tab === 'detail' && selectedModel && (
          <ModelDetail model={selectedModel} recordCount={records.length} />
        )}
      </div>
    </div>
  );
}

/* ===== 模型列表 ===== */
function ModelsList({ models, loading, search, setSearch, onSelect, onDelete, onRefresh }: {
  models: DataModel[]; loading: boolean; search: string; setSearch: (s: string) => void;
  onSelect: (m: DataModel) => void; onDelete: (id: string) => void; onRefresh: () => void;
}) {
  const [showCreate, setShowCreate] = useState(false);

  const createModel = async (name: string, desc: string, color: string) => {
    await api('', { method: 'POST', body: JSON.stringify({ name, description: desc, color, fields: [] }) });
    onRefresh();
    setShowCreate(false);
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
          <input value={search} onChange={e => setSearch(e.target.value)}
            placeholder="搜索数据模型..." className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-slate-800 border border-slate-600 text-sm focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none" />
        </div>
        <button onClick={() => setShowCreate(true)}
          className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-500 text-sm font-medium shadow-lg shadow-blue-600/20 transition">
          <Plus className="w-4 h-4" />新建模型
        </button>
      </div>

      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {[1, 2, 3].map(i => <div key={i} className="h-40 rounded-xl bg-slate-800/50 animate-pulse" />)}
        </div>
      ) : models.length === 0 ? (
        <div className="text-center py-20 text-slate-400">
          <Database className="w-16 h-16 mx-auto mb-4 opacity-30" />
          <p className="text-lg">暂无数据模型</p>
          <p className="text-sm mt-1">点击「新建模型」创建您的第一个数据模型</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {models.map(m => (
            <div key={m.id} onClick={() => onSelect(m)}
              className="group relative p-5 rounded-xl bg-slate-800/70 border border-slate-700 hover:border-blue-500/40 hover:shadow-[0_0_20px_rgba(59,130,246,0.08)] cursor-pointer transition-all">
              <div className="flex items-start justify-between mb-3">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-lg flex items-center justify-center text-lg"
                    style={{ background: `${m.color || '#3b82f6'}20`, color: m.color || '#3b82f6' }}>
                    <Database className="w-5 h-5" />
                  </div>
                  <div>
                    <h3 className="font-semibold text-slate-100">{m.name}</h3>
                    <p className="text-xs text-slate-400">{m.fields?.length ?? 0} 个字段</p>
                  </div>
                </div>
                <button onClick={e => { e.stopPropagation(); onDelete(m.id); }}
                  className="opacity-0 group-hover:opacity-100 p-1.5 rounded-lg hover:bg-red-500/20 text-slate-400 hover:text-red-400 transition">
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
              {m.description && <p className="text-sm text-slate-400 mb-3 line-clamp-2">{m.description}</p>}
              <div className="flex items-center justify-between text-xs text-slate-500">
                <span>{m.recordCount ?? 0} 条记录</span>
                <ChevronRight className="w-4 h-4 group-hover:text-blue-400 transition" />
              </div>
            </div>
          ))}
        </div>
      )}

      {/* 新建弹窗 */}
      {showCreate && <CreateModelModal onCreate={createModel} onClose={() => setShowCreate(false)} />}
    </div>
  );
}

function CreateModelModal({ onCreate, onClose }: { onCreate: (n: string, d: string, c: string) => void; onClose: () => void; }) {
  const [name, setName] = useState('');
  const [desc, setDesc] = useState('');
  const [color, setColor] = useState(MODEL_COLORS[0]);
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm" onClick={onClose}>
      <div className="w-full max-w-md p-6 rounded-2xl bg-slate-800 border border-slate-600 shadow-2xl" onClick={e => e.stopPropagation()}>
        <h2 className="text-lg font-bold mb-4">新建数据模型</h2>
        <label className="block text-sm text-slate-300 mb-1">模型名称</label>
        <input value={name} onChange={e => setName(e.target.value)} placeholder="如：供应商信息、产品规格"
          className="w-full px-3 py-2 rounded-lg bg-slate-700 border border-slate-600 text-sm mb-3 focus:border-blue-500 outline-none" />
        <label className="block text-sm text-slate-300 mb-1">描述</label>
        <textarea value={desc} onChange={e => setDesc(e.target.value)} placeholder="可选" rows={2}
          className="w-full px-3 py-2 rounded-lg bg-slate-700 border border-slate-600 text-sm mb-3 resize-none focus:border-blue-500 outline-none" />
        <label className="block text-sm text-slate-300 mb-2">颜色</label>
        <div className="flex gap-2 mb-5">
          {MODEL_COLORS.map(c => (
            <button key={c} onClick={() => setColor(c)}
              className={`w-8 h-8 rounded-lg transition ${color === c ? 'ring-2 ring-white ring-offset-2 ring-offset-slate-800' : 'hover:scale-110'}`}
              style={{ background: c }} />
          ))}
        </div>
        <div className="flex justify-end gap-3">
          <button onClick={onClose} className="px-4 py-2 rounded-lg bg-slate-700 text-sm">取消</button>
          <button onClick={() => name.trim() && onCreate(name.trim(), desc, color)} disabled={!name.trim()}
            className="px-4 py-2 rounded-lg bg-blue-600 hover:bg-blue-500 text-sm font-medium disabled:opacity-50">创建</button>
        </div>
      </div>
    </div>
  );
}

/* ===== 字段编辑器 ===== */
function FieldsEditor({ model, onSave }: { model: DataModel; onSave: (m: DataModel) => void; }) {
  const [fields, setFields] = useState<FieldDef[]>(model.fields ?? []);
  const [editing, setEditing] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  // 当 model.fields 从后端加载完成后同步到本地 state
  useEffect(() => {
    setFields(model.fields ?? []);
  }, [model.fields]);

  const addField = () => {
    const f: FieldDef = {
      id: `field_${Date.now()}`, name: `field_${fields.length + 1}`, label: `字段${fields.length + 1}`,
      type: 'text', required: false, orderNum: fields.length + 1, visible: true, searchable: false
    };
    setFields([...fields, f]);
    setEditing(f.id!);
  };

  const updateField = (id: string, patch: Partial<FieldDef>) => {
    setFields(fields.map(f => f.id === id ? { ...f, ...patch } : f));
  };

  const removeField = (id: string) => setFields(fields.filter(f => f.id !== id));

  const moveField = (idx: number, dir: -1 | 1) => {
    const target = idx + dir;
    if (target < 0 || target >= fields.length) return;
    const arr = [...fields];
    [arr[idx], arr[target]] = [arr[target], arr[idx]];
    arr.forEach((f, i) => f.orderNum = i + 1);
    setFields(arr);
  };

  const save = async () => {
    setSaving(true);
    await onSave({ ...model, fields });
    setSaving(false);
    setEditing(null);
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-lg font-bold flex items-center gap-2">
          <Settings className="w-5 h-5 text-blue-400" />字段配置
          <span className="text-sm font-normal text-slate-400">（{fields.length} 个字段）</span>
        </h2>
        <div className="flex gap-2">
          <button onClick={addField} className="flex items-center gap-1 px-3 py-2 rounded-lg bg-blue-600 hover:bg-blue-500 text-sm transition">
            <Plus className="w-4 h-4" />添加字段
          </button>
          <button onClick={save} disabled={saving} className="flex items-center gap-1 px-3 py-2 rounded-lg bg-green-600 hover:bg-green-500 text-sm transition disabled:opacity-50">
            <Save className="w-4 h-4" />{saving ? '保存中...' : '保存'}
          </button>
        </div>
      </div>

      {fields.length === 0 ? (
        <div className="text-center py-16 text-slate-400">
          <Settings className="w-12 h-12 mx-auto mb-3 opacity-30" />
          <p>暂无字段，点击「添加字段」开始配置</p>
        </div>
      ) : (
        <div className="space-y-2">
          {fields.map((f, idx) => (
            <div key={f.id} className={`group rounded-xl border transition ${editing === f.id ? 'bg-slate-800 border-blue-500/40' : 'bg-slate-800/50 border-slate-700 hover:border-slate-600'}`}>
              {editing === f.id ? (
                /* 编辑模式 */
                <div className="p-4 space-y-3">
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="block text-xs text-slate-400 mb-1">字段名（英文标识）</label>
                      <input value={f.name} onChange={e => updateField(f.id!, { name: e.target.value.replace(/\s/g, '_') })}
                        className="w-full px-3 py-1.5 rounded-lg bg-slate-700 border border-slate-600 text-sm focus:border-blue-500 outline-none" />
                    </div>
                    <div>
                      <label className="block text-xs text-slate-400 mb-1">显示标签</label>
                      <input value={f.label} onChange={e => updateField(f.id!, { label: e.target.value })}
                        className="w-full px-3 py-1.5 rounded-lg bg-slate-700 border border-slate-600 text-sm focus:border-blue-500 outline-none" />
                    </div>
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="block text-xs text-slate-400 mb-1">类型</label>
                      <select value={f.type} onChange={e => updateField(f.id!, { type: e.target.value as FieldDef['type'] })}
                        className="w-full px-3 py-1.5 rounded-lg bg-slate-700 border border-slate-600 text-sm focus:border-blue-500 outline-none">
                        {FIELD_TYPES.map(t => <option key={t} value={t}>{TYPE_LABELS[t]}</option>)}
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs text-slate-400 mb-1">默认值</label>
                      <input value={f.defaultValue ?? ''} onChange={e => updateField(f.id!, { defaultValue: e.target.value })}
                        className="w-full px-3 py-1.5 rounded-lg bg-slate-700 border border-slate-600 text-sm focus:border-blue-500 outline-none" />
                    </div>
                  </div>
                  {f.type === 'select' && (
                    <div>
                      <label className="block text-xs text-slate-400 mb-1">选项（逗号分隔）</label>
                      <input value={f.options ?? ''} onChange={e => updateField(f.id!, { options: e.target.value })}
                        placeholder="选项1,选项2,选项3"
                        className="w-full px-3 py-1.5 rounded-lg bg-slate-700 border border-slate-600 text-sm focus:border-blue-500 outline-none" />
                    </div>
                  )}
                  <div className="flex items-center gap-4">
                    <button onClick={() => updateField(f.id!, { required: !f.required })}
                      className="flex items-center gap-1.5 text-sm text-slate-300 hover:text-white transition">
                      {f.required ? <ToggleRight className="w-5 h-5 text-blue-400" /> : <ToggleLeft className="w-5 h-5 text-slate-500" />}
                      必填
                    </button>
                    <button onClick={() => updateField(f.id!, { visible: !f.visible })}
                      className="flex items-center gap-1.5 text-sm text-slate-300 hover:text-white transition">
                      {f.visible ? <Eye className="w-4 h-4 text-blue-400" /> : <EyeOff className="w-4 h-4 text-slate-500" />}
                      列表可见
                    </button>
                    <button onClick={() => updateField(f.id!, { searchable: !f.searchable })}
                      className="flex items-center gap-1.5 text-sm text-slate-300 hover:text-white transition">
                      {f.searchable ? <ToggleRight className="w-5 h-5 text-blue-400" /> : <ToggleLeft className="w-5 h-5 text-slate-500" />}
                      可搜索
                    </button>
                  </div>
                  <div className="flex justify-end">
                    <button onClick={() => setEditing(null)} className="px-3 py-1.5 rounded-lg bg-slate-700 text-sm hover:bg-slate-600 transition">
                      <X className="w-4 h-4" />
                    </button>
                  </div>
                </div>
              ) : (
                /* 显示模式 */
                <div className="flex items-center gap-3 p-3">
                  <div className="flex flex-col gap-0.5 opacity-40">
                    <button onClick={() => moveField(idx, -1)} className="p-0.5 hover:text-white"><GripVertical className="w-4 h-3" /></button>
                  </div>
                  <div className="px-2 py-0.5 rounded text-xs font-mono bg-slate-700 text-slate-300">{f.name}</div>
                  <div className="flex-1 font-medium text-sm">{f.label}</div>
                  <span className="px-2 py-0.5 rounded text-xs bg-blue-500/20 text-blue-300">{TYPE_LABELS[f.type]}</span>
                  {f.required && <span className="px-2 py-0.5 rounded text-xs bg-red-500/20 text-red-300">必填</span>}
                  {!f.visible && <EyeOff className="w-3.5 h-3.5 text-slate-500" />}
                  {f.searchable && <Search className="w-3.5 h-3.5 text-green-400" />}
                  <button onClick={() => setEditing(f.id!)} className="p-1 rounded hover:bg-slate-600 text-slate-400 hover:text-white transition">
                    <Edit3 className="w-4 h-4" />
                  </button>
                  <button onClick={() => removeField(f.id!)} className="p-1 rounded hover:bg-red-500/20 text-slate-400 hover:text-red-400 transition">
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/* ===== 数据记录 ===== */
function RecordsView({ model, records, onRefresh }: { model: DataModel; records: DataRecord[]; onRefresh: () => void; }) {
  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState<Record<string, unknown>>({});
  const [editingId, setEditingId] = useState<string | null>(null);
  const visibleFields = (model.fields ?? []).filter(f => f.visible);

  const createRecord = async () => {
    await api(`/${model.id}/records`, { method: 'POST', body: JSON.stringify({ data: formData }) });
    setShowForm(false); setFormData({}); setEditingId(null);
    onRefresh();
  };

  const updateRecord = async (id: string) => {
    await api(`/${model.id}/records/${id}`, { method: 'PUT', body: JSON.stringify({ data: formData }) });
    setShowForm(false); setFormData({}); setEditingId(null);
    onRefresh();
  };

  const deleteRecord = async (id: string) => {
    if (!confirm('确定删除此记录？')) return;
    await api(`/${model.id}/records/${id}`, { method: 'DELETE' });
    onRefresh();
  };

  const startEdit = (r: DataRecord) => {
    setFormData(r.data); setEditingId(r.id); setShowForm(true);
  };

  const renderInput = (f: FieldDef) => {
    const val = formData[f.name] ?? f.defaultValue ?? '';
    if (f.type === 'boolean') return (
      <button type="button" onClick={() => setFormData({ ...formData, [f.name]: !val })}
        className="flex items-center gap-2 text-sm">
        {val ? <ToggleRight className="w-6 h-6 text-blue-400" /> : <ToggleLeft className="w-6 h-6 text-slate-500" />}
        {val ? '是' : '否'}
      </button>
    );
    if (f.type === 'select') return (
      <select value={String(val)} onChange={e => setFormData({ ...formData, [f.name]: e.target.value })}
        className="w-full px-3 py-2 rounded-lg bg-slate-700 border border-slate-600 text-sm focus:border-blue-500 outline-none">
        <option value="">请选择</option>
        {(f.options ?? '').split(',').map(o => o.trim()).filter(Boolean).map(o => <option key={o} value={o}>{o}</option>)}
      </select>
    );
    if (f.type === 'textarea') return (
      <textarea value={String(val)} onChange={e => setFormData({ ...formData, [f.name]: e.target.value })} rows={3}
        className="w-full px-3 py-2 rounded-lg bg-slate-700 border border-slate-600 text-sm resize-none focus:border-blue-500 outline-none" />
    );
    return (
      <input type={f.type === 'date' ? 'date' : f.type === 'number' ? 'number' : f.type === 'email' ? 'email' : f.type === 'url' ? 'url' : 'text'}
        value={String(val)} onChange={e => setFormData({ ...formData, [f.name]: f.type === 'number' ? Number(e.target.value) : e.target.value })}
        className="w-full px-3 py-2 rounded-lg bg-slate-700 border border-slate-600 text-sm focus:border-blue-500 outline-none" />
    );
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-lg font-bold flex items-center gap-2">
          <Table2 className="w-5 h-5 text-green-400" />数据记录
          <span className="text-sm font-normal text-slate-400">（{records.length} 条）</span>
        </h2>
        <button onClick={() => { setFormData({}); setEditingId(null); setShowForm(true); }}
          className="flex items-center gap-1 px-3 py-2 rounded-lg bg-green-600 hover:bg-green-500 text-sm transition">
          <Plus className="w-4 h-4" />新增记录
        </button>
      </div>

      {/* 动态表单弹窗 */}
      {showForm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm" onClick={() => setShowForm(false)}>
          <div className="w-full max-w-lg max-h-[80vh] overflow-y-auto p-6 rounded-2xl bg-slate-800 border border-slate-600 shadow-2xl" onClick={e => e.stopPropagation()}>
            <h3 className="text-lg font-bold mb-4">{editingId ? '编辑记录' : '新增记录'}</h3>
            <div className="space-y-4">
              {(model.fields ?? []).map(f => (
                <div key={f.id}>
                  <label className="block text-sm text-slate-300 mb-1">
                    {f.label}{f.required && <span className="text-red-400 ml-1">*</span>}
                  </label>
                  {renderInput(f)}
                </div>
              ))}
            </div>
            <div className="flex justify-end gap-3 mt-6">
              <button onClick={() => setShowForm(false)} className="px-4 py-2 rounded-lg bg-slate-700 text-sm">取消</button>
              <button onClick={editingId ? () => updateRecord(editingId) : createRecord}
                className="px-4 py-2 rounded-lg bg-blue-600 hover:bg-blue-500 text-sm font-medium">
                {editingId ? '更新' : '创建'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 记录表格 */}
      {records.length === 0 ? (
        <div className="text-center py-16 text-slate-400">
          <Table2 className="w-12 h-12 mx-auto mb-3 opacity-30" />
          <p>暂无数据记录</p>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-xl border border-slate-700">
          <table className="w-full text-sm">
            <thead className="bg-slate-800/80">
              <tr>
                {visibleFields.map(f => <th key={f.id} className="px-4 py-3 text-left text-slate-300 font-medium">{f.label}</th>)}
                <th className="px-4 py-3 text-right text-slate-300 font-medium w-24">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-700/50">
              {records.map(r => (
                <tr key={r.id} className="hover:bg-slate-800/50 transition">
                  {visibleFields.map(f => (
                    <td key={f.id} className="px-4 py-3 text-slate-200">
                      {f.type === 'boolean' ? (r.data[f.name] ? '✓' : '✗') : String(r.data[f.name] ?? '-')}
                    </td>
                  ))}
                  <td className="px-4 py-3 text-right">
                    <div className="flex items-center justify-end gap-1">
                      <button onClick={() => startEdit(r)} className="p-1 rounded hover:bg-slate-600 text-slate-400 hover:text-blue-400 transition"><Edit3 className="w-4 h-4" /></button>
                      <button onClick={() => deleteRecord(r.id)} className="p-1 rounded hover:bg-red-500/20 text-slate-400 hover:text-red-400 transition"><Trash2 className="w-4 h-4" /></button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

/* ===== 模型详情 ===== */
function ModelDetail({ model, recordCount }: { model: DataModel; recordCount: number; }) {
  return (
    <div className="space-y-6">
      <div className="p-6 rounded-xl bg-slate-800/70 border border-slate-700">
        <div className="flex items-center gap-4 mb-4">
          <div className="w-14 h-14 rounded-xl flex items-center justify-center text-2xl"
            style={{ background: `${model.color || '#3b82f6'}20`, color: model.color || '#3b82f6' }}>
            <Database className="w-7 h-7" />
          </div>
          <div>
            <h2 className="text-xl font-bold">{model.name}</h2>
            <p className="text-sm text-slate-400">{model.description || '无描述'}</p>
          </div>
        </div>
        <div className="grid grid-cols-3 gap-4">
          <div className="p-3 rounded-lg bg-slate-700/50 text-center">
            <p className="text-2xl font-bold text-blue-400">{model.fields?.length ?? 0}</p>
            <p className="text-xs text-slate-400">字段数</p>
          </div>
          <div className="p-3 rounded-lg bg-slate-700/50 text-center">
            <p className="text-2xl font-bold text-green-400">{recordCount}</p>
            <p className="text-xs text-slate-400">记录数</p>
          </div>
          <div className="p-3 rounded-lg bg-slate-700/50 text-center">
            <p className="text-2xl font-bold text-amber-400">{(model.fields ?? []).filter(f => f.required).length}</p>
            <p className="text-xs text-slate-400">必填字段</p>
          </div>
        </div>
      </div>
      <div className="p-6 rounded-xl bg-slate-800/70 border border-slate-700">
        <h3 className="font-bold mb-3 flex items-center gap-2"><Settings className="w-4 h-4 text-blue-400" />字段列表</h3>
        <div className="space-y-2">
          {(model.fields ?? []).map(f => (
            <div key={f.id} className="flex items-center gap-3 p-3 rounded-lg bg-slate-700/30">
              <span className="px-2 py-0.5 rounded text-xs font-mono bg-slate-700 text-slate-300">{f.name}</span>
              <span className="flex-1 text-sm">{f.label}</span>
              <span className="px-2 py-0.5 rounded text-xs bg-blue-500/20 text-blue-300">{TYPE_LABELS[f.type]}</span>
              {f.required && <span className="px-2 py-0.5 rounded text-xs bg-red-500/20 text-red-300">必填</span>}
              {f.searchable && <span className="px-2 py-0.5 rounded text-xs bg-green-500/20 text-green-300">可搜索</span>}
            </div>
          ))}
        </div>
      </div>
      <div className="text-xs text-slate-500">
        创建于 {new Date(model.createdAt).toLocaleString()} · 更新于 {new Date(model.updatedAt).toLocaleString()}
      </div>
    </div>
  );
}

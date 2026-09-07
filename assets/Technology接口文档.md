# Technology 工艺模块接口文档


## 接口一览(共 6 个)

| 接口(页面) | 功能 | 页面说明注释 |
|---|---|---|
| `NGyMainQuery.aspx` | 内衣(针织)工艺单主表信息查询 | 获取内衣工艺单主表信息 |
| `SGyMainQuery.aspx` | 丝袜工艺单主表信息查询 | 获取丝袜工艺单主表信息 |
| `NGyBujQuery.aspx` | 内衣货号工艺部件查询 | 获取内衣工艺部件 |
| `NGyWorkTypeQuery.aspx` | 内衣货号工艺工序查询 | 获取内衣工艺工序 |
| `NGyHuohaoPriceQuery.aspx` | 内衣货号工序工价查询 | 获取内衣工序工价 |
| `MaterialYLQuery.aspx` | 原料物料信息查询(原料 BOM) | 获取物料原料信息 |

---

## 公共约定

| 项目 | 说明 |
|---|---|
| 接口地址 | `POST Technology/<页面名>.aspx`(以下各接口 URL 均相对站点根,前面拼虚拟目录) |
| 请求方式 | POST(表单 `application/x-www-form-urlencoded`)或 GET(QueryString)均可 —— 服务端读取顺序为 **Form → QueryString**;演示统一用 POST |
| 入参绑定 | 按入参对象属性名自动绑定(`RequestHelper.GetClass<T>`),支持 int/decimal/DateTime/string 等,传空串视为无此条件 |
| 字符编码 | UTF-8 |

```json
{
    "code": 1,          // 1 成功 / 0 失败 / -100 鉴权失败 / -101 授权超时 / -200 无效账套码
    "message": "",      // 失败原因(中文,面向用户)
    "result": null      // 数据区,各接口不同
}
```

> 说明:本模块 6 个接口服务端均**不抛业务异常**(纯查询,条件为空时返回空列表/空对象),失败主要来自鉴权与网络。

---

## 一、内衣工艺单主表信息查询 `NGyMainQuery`

### 基本信息

| 项目 | 内容 |
|---|---|
| 接口地址 | `POST  Technology/NGyMainQuery.aspx` |
| 功能 | 分页式(本接口不分页)模糊查询**内衣工艺单主表** |

### 请求参数(全部可选,均模糊匹配 `LIKE`)

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `bh` | string | 否 | 编号 |
| `hhtype` | string | 否 |货号类别
| `huohao` | string | 否 |货号
| `spname` | string | 否 | 品名 |
| `designer` | string | 否 | 设计师 |
| `dw` | string | 否 | 单位 |
| `rsjgh` | string | 否 | 染色厂名称 |
| `qd_dys` | string | 否 | 前道打样师傅 |
| `hd_dys` | string | 否 | 后道打样师傅 |
| `dybanhao` | string | 否 | 打样版号 |

### 返回 result(数组 `NGyMainQueryOutput`)

| 字段名 | 类型 | 说明 |
|---|---|---|
| `bh` | string | 编号(货号内码) |
| `hhtype` | string | 货号类别名称 |
| `huohao` | string | 货号 |
| `spname` | string | 品名 |
| `designer` | string | 设计师 |
| `dw` | string | 单位 |
| `rsjgh` | string | 染色厂名称 |
| `qd_dys` | string | 前道打样师傅 |
| `hd_dys` | string | 后道打样师傅 |
| `dybanhao` | string | 打样版号 |
| `remark` | string | 备注 |


---

## 二、丝袜工艺单主表信息查询 `SGyMainQuery`

### 基本信息

| 项目 | 内容 |
|---|---|
| 接口地址 | `POST Technology/SGyMainQuery.aspx` |
| 功能 | 模糊查询**丝袜工艺单主表**,|

### 请求参数(全部可选,均模糊匹配)

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `bh` | string | 否 | 编号(内码) |
| `hhtype` | string | 否 | 产品类别名称 |
| `huohao` | string | 否 | 货号 |
| `spname` | string | 否 | 品名 |
| `dw` | string | 否 | 单位 |
| `dybanhao` | string | 否 | 版号(打样版号) |
| `cxm` | string | 否 | 程序名 |
| `jix` | string | 否 | 机型 |
| `zs` | string | 否 | 针数 |
| `yajiao` | string | 否 | 压脚 |
| `nd` | string | 否 | 牛顿 |
| `hhywy` | string | 否 | 业务员姓名 |
| `qd_dys` | string | 否 | 前道打样师姓名 |
| `hd_dys` | string | 否 | 后道打样师姓名 |
| `gylc` | string | 否 | 工艺流程 |

### 返回 result(数组 `SGyMainQueryOutput`)

| 字段名 | 类型 | 说明 |
|---|---|---|
| `bh` | string | 编号(内码) |
| `hhtype` | string | 货号类别名称 |
| `huohao` | string | 生产货号 |
| `spname` | string | 品名 |
| `dw` | string | 单位 |
| `dybanhao` | string | 版本号/版号 |
| `cxm` | string | 程序名 |
| `xjkz` | decimal? | 下机克重 |
| `xjsl` | decimal? | 下机秒数 |
| `pfkz` | decimal? | 缝拼克重 |
| `cpkz` | decimal? | 成品克重 |
| `zcl` | decimal? | 制成率 |
| `jix` | string | 机型 |
| `zs` | string | 针数 |
| `yajiao` | string | 压脚 |
| `nd` | string | 牛顿 |
| `djcl` | decimal? | 理论产量 |
| `hhywy` | string | 业务员 |
| `qd_dys` | string | 前道打样师 |
| `hd_dys` | string | 后道打样师 |
| `gylc` | string | 工艺流程 |
| `remark` | string | 总备注 |

---

## 三、内衣工艺部件查询 `NGyBuj`

### 基本信息

| 项目 | 内容 |
|---|---|
| 接口地址 | `POST Technology/NGyBujQuery.aspx` |
| 功能 | 查询**内衣货号的工艺部件明细 |

### 请求参数(全部可选,均模糊匹配)

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `hhname` | string | 否 | 货号名称 |
| `color` | string | 否 | 颜色 |
| `chima` | string | 否 | 尺码 |
| `buj` | string | 否 | 部件 |
| `cxm` | string | 否 | 程序名 |
| `jix` | string | 否 | 机型 |

### 返回 result(数组 `NGyBujQueryOutput`)

| 字段名 | 类型 | 说明 |
|---|---|---|
| `hhname` | string | 货号 |
| `color` | string | 颜色 |
| `chima` | string | 尺码 |
| `buj` | string | 部件 |
| `zbj` | int? | 主部件:1 是 / 0 不是 |
| `jix` | string | 机型 |
| `zs` | int? | 针数 |
| `cxm` | string | 程序名 |
| `tongjing` | decimal? | 口径 |
| `bili` | string | 比例 |
| `kez` | decimal? | 克重 |
| `xjtime` | decimal? | 下机时间 |
| `tjcxm` | string | 调机程序名 |
| `tjxs` | string | 调机线速 |
| `tzs` | string | 提字色 |
| `skzjj` | string | 圣克罩间距 |
| `xf` | string | 吸风 M/S |
| `zznd` | string | 织造难度 |
| `llcl` | decimal? | 理论产量 |
| `remark` | string | 备注 |
| `vchima` | string | 校验尺码 |
| `vcolor` | string | 校验颜色 |
| `vtzs` | string | 校验提字色 |
| `ischeck` | string | 确认状态:是/否|
| `isrecheck` | string | 审核状态:是/否

---

## 四、内衣工艺工序查询 `NGyWorkTypeQuery`

### 基本信息

| 项目 | 内容 |
|---|---|
| 接口地址 | `POST Technology/NGyWorkTypeQuery.aspx` |
| 功能 | 查询**内衣货号的生产工序明细 |

### 请求参数(全部可选,均模糊匹配)

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `hhname` | string | 否 | 货号名称 |
| `wtname` | string | 否 | 工序|
| `tjtype` | string | 否 | 统计类型 |
| `sctype` | string | 否 | 生产类型名称|


| 字段名 | 类型 | 说明 |
|---|---|---|
| `hhname` | string | 货号 |
| `wtname` | string | 工序名称 |
| `jizhong` | string | 机种 |
| `zhenju` | string | 针目)|
| `zhenhao` | string | 针号 |
| `zhenmu` | string | 针距 |
| `zhens` | string | 针数 |
| `zline` | string | 缝线上 |
| `sline` | string | 缝线下 |
| `yongl` | decimal? | 用量/CM 上 |
| `yongl2` | string | 用量/CM 下 |
| `sort` | int | 排序号 |
| `tjtype` | string | 统计类型 |
| `sctype` | string | 生产类型 |
| `using_state` | string | 使用中:是/否 |
| `zhgx` | string | 最后工序:是/否 |
| `tims` | decimal? | 用时(秒) |
| `ischeck` | string | 确认状态是/否 |
| `isrecheck` | string | 审核状态:是/否 |

---

## 五、内衣工序工价查询 `NGyHuohaoPriceQuery`

### 基本信息

| 项目 | 内容 |
|---|---|
| 接口地址 | `POST Technology/NGyHuohaoPriceQuery.aspx` |
| 功能 | 查询当前**生效中的内衣货号工序工价**, |

### 请求参数(全部可选,均模糊匹配)

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `hhname` | string | 否 | 货号名称 |
| `wtname` | string | 否 | 工序名称 |


| 字段名 | 类型 | 说明 |
|---|---|---|
| `hhname` | string | 货号 |
| `wtname` | string | 工序 |
| `jsprice` | decimal? | 技术工价 |
| `price` | decimal? | 工价|
| `tempworker_price` | decimal? | 临时工价 |
| `state` | string | 审核状态:未审核 / 已审核 |


---

## 六、原料物料信息查询(原料 BOM)`MaterialYLQuery`

### 基本信息

| 项目 | 内容 |
|---|---|
| 接口地址 | `POST Technology/MaterialYLQuery.aspx` |
| 功能 | 查询**原料级 BOM/物料档案**|

### 请求参数(全部可选,均模糊匹配)

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `wlname` | string | 否 | 物料名称
| `hhname` | string | 否 | 货号名称 |
| `color` | string | 否 | 颜色 |
| `chima` | string | 否 | 尺码 |
| `dw` | string | 否 | 单位 |
| `buj` | string | 否 | 部件 |
| `gys` | string | 否 | 供应商名称 |
| `guige` | string | 否 | 规格 |
| `pihao` | string | 否 | 批号 |
| `nianx` | string | 否 | 捻向 |

### 返回 result(数组 `MaterialYLQueryOutput`)

| 字段名 | 类型 | 说明 |
|---|---|---|
| `hhname` | string | 货号 |
| `color` | string | 颜色 |
| `chima` | string | 尺码 |
| `buj` | string | 部件 |
| `gys` | string | 供应商名称 |
| `wlname` | string | 物料名称 |
| `guige` | string | 规格 |
| `wlcolor` | string | 物料颜色 |
| `pihao` | string | 批号 |
| `nianx` | string | 捻向 |
| `dw` | string | 单位 |
| `djyl` | decimal? | 单件用量 |
| `sh` | decimal? | 损耗 |
| `bl` | decimal? | 比例 |
| `price` | decimal? | 单价 |
| `je` | decimal? | 金额 |
| `remark` | string | 备注 |

---


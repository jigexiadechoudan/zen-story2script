# 剧本 YAML Schema v1.0

本文定义 Zen Story2Script 的结构化剧本输出格式。该 Schema 面向“小说 3 个章节以上自动改编为可编辑剧本初稿”的比赛要求，不把输出停留在剧情梗概或场景大纲，而是要求每个场景包含演员能直接参照排练的动作、对白和表演提示。

## 设计目标

- 可表演：每个场景必须有地点、时间、出场人物、动作 beat 和对白 beat，避免只输出“发现线索”“完成对质”这类抽象描述。
- 可编辑：作者可以按角色、场景、beat 精确修改对白、动作和节奏。
- 可校验：后端可以稳定检查 YAML 语法、顶层字段、场景类型、beat 类型以及动作/对白最低完整度。
- 可导出：`scenes[].beats` 可以进一步渲染为 Markdown 剧本、分场表、拍摄清单或演员台词本。
- 可扩展：通过 `schema_version` 为后续分镜、镜头、集数、场记和制作字段留出版本演进空间。

## 顶层结构

v1.0 的 YAML 根节点必须是对象，并且顶层字段固定为：

```yaml
schema_version: "1.0"
work: {}
adaptation: {}
characters: []
plot_outline: []
scenes: []
notes: {}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `schema_version` | string | 是 | Schema 版本，当前固定为 `"1.0"` |
| `work` | object | 是 | 原小说作品信息 |
| `adaptation` | object | 是 | 改编目标、基调和创作原则 |
| `characters` | list | 是 | 角色表 |
| `plot_outline` | list | 是 | 小说章节到剧本事件的改编说明 |
| `scenes` | list | 是 | 可表演剧本场景 |
| `notes` | object | 是 | 删减、风险和后续打磨建议 |

顶层字段不允许自由新增。原因是比赛演示和后续导出都需要稳定结构；如果模型随意增加字段，前端预览、下载和修复工具会变得不可预测。

## 字段定义

### work

```yaml
work:
  title: "雾镇来信"
  original_author: ""
  language: "zh-CN"
  source_chapters:
    count: 3
    range: "第一章至第三章"
```

`work` 保存原作元信息。`source_chapters.count` 用于说明输入满足“3 个章节以上”的转换要求，也方便作者判断输出覆盖范围。

### adaptation

```yaml
adaptation:
  target_format: "short_drama"
  target_duration: "10-15min"
  genre: "悬疑"
  tone: "现实主义、节奏紧凑、冷峻克制"
  logline: "一段来自旧事件的线索，推动主角追问真相并完成改编。"
  principles:
    - "保留核心悬念，把内心独白外化为动作和对白。"
```

`adaptation` 记录改编方向。这样作者能快速判断 AI 是否理解目标格式、时长、类型和风格，而不是只能从最终剧本里反推。

### characters

```yaml
characters:
  - id: "char_001"
    name: "主角"
    role: "protagonist"
    identity: "追问旧事件的核心人物"
    personality: "克制、敏感"
    goal: "找到可以公开的真相。"
    arc: "从被动收到线索到主动对质。"
    relationships:
      - target: "关键证人"
        relation: "追问与隐瞒"
```

角色表不只是名单，还需要保留人物目标和关系。原因是剧本可表演性来自冲突：演员需要知道角色为什么说这句话、为什么在这一场选择行动或沉默。

### plot_outline

```yaml
plot_outline:
  - source_chapter: "第一章至第三章"
    key_events:
      - "主角收到旧事件线索。"
      - "关键证人在对质中暴露真相缝隙。"
    adaptation_choice: "将小说叙述信息改写为可表演的动作和对白。"
```

`plot_outline` 是小说与剧本之间的桥。它说明 AI 如何压缩、合并或重排章节，便于作者追溯改编取舍。

### scenes

```yaml
scenes:
  - scene_id: "S001"
    scene_type: "INT"
    location: "旧屋客厅"
    time_of_day: "NIGHT"
    characters:
      - "主角"
      - "关键证人"
    summary: "主角用来信逼问关键证人。"
    dramatic_purpose: "把旧事件线索转化为人物冲突。"
    beats:
      - type: "action"
        content: "主角把来信放在台灯下，等关键证人看清署名。"
      - type: "dialogue"
        speaker: "主角"
        content: "别再替我概括了。把那天晚上你没说出口的话说出来。"
```

场景字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `scene_id` | string | 场景编号，建议 `S001` 格式 |
| `scene_type` | string | `INT`、`EXT` 或 `INT/EXT` |
| `location` | string | 具体地点 |
| `time_of_day` | string | 时间，如 `DAY`、`NIGHT`、`DUSK` |
| `characters` | list | 本场出场人物 |
| `summary` | string | 本场剧情摘要 |
| `dramatic_purpose` | string | 本场在剧作结构中的作用 |
| `beats` | list | 本场动作、对白、括号提示和转场 |

`summary` 和 `dramatic_purpose` 是给作者和编辑看的；`beats` 是给演员和导演看的。因此校验器会要求每场至少有一个 `action` 和一个 `dialogue`。

### beats

```yaml
beats:
  - type: "action"
    content: "主角把录音机推到桌面中央，手指停在播放键上。"
  - type: "dialogue"
    speaker: "关键证人"
    content: "那晚不是钟慢了，是我们让所有人相信钟慢了。"
  - type: "parenthetical"
    content: "主角没有立刻追问，只把录音机按下。"
  - type: "transition"
    content: "CUT TO:"
```

beat 类型：

| 类型 | 用途 |
| --- | --- |
| `action` | 可见动作、场面调度、道具、沉默、反应 |
| `dialogue` | 可直接说出口的台词，必须包含 `speaker` |
| `parenthetical` | 表演提示、语气、短暂停顿或括号说明 |
| `transition` | 转场提示 |

设计原因：小说改编的难点不是“知道发生了什么”，而是把叙述变成可拍、可演、可修改的页面。`beats` 用顺序结构保留场内节奏，既能读，又能被程序导出。

### notes

```yaml
notes:
  adaptation_summary: "三章小说被压缩为三场短剧。"
  omitted_elements:
    - "支线人物的童年回忆。"
  risks:
    - "反派动机仍需在长版本中补强。"
  next_steps:
    - "补写最终对质场的第二轮台词。"
```

`notes` 明确 AI 输出只是初稿。它记录删减内容、潜在问题和下一步打磨方向，帮助作者继续创作。

## 当前校验规则

后端 `YamlSchemaValidator` 会检查：

- YAML 内容不能为空。
- YAML 必须能被安全解析。
- 根节点必须是对象。
- 顶层字段必须包含且仅包含 `schema_version`、`work`、`adaptation`、`characters`、`plot_outline`、`scenes`、`notes`。
- `schema_version` 必须等于 `"1.0"`。
- `work`、`adaptation`、`notes` 必须是对象。
- `characters`、`plot_outline`、`scenes` 必须是列表。
- 每个 `scenes[]` 必须是对象。
- 每个场景必须包含非空 `scene_id`、`scene_type`、`location`、`time_of_day`、`characters`、`summary`、`dramatic_purpose`、`beats`。
- `scene_type` 必须是 `INT`、`EXT` 或 `INT/EXT`。
- 每个 `beats[]` 必须是对象，`type` 必须是 `action`、`dialogue`、`parenthetical` 或 `transition`。
- 每个 beat 必须包含非空 `content`。
- `dialogue` beat 必须包含非空 `speaker`。
- 每个场景至少包含一个 `action` beat 和一个 `dialogue` beat。

校验器只判断结构和最低可表演性，不评价文学质量。人物弧光、对白力度、节奏强弱仍需要作者继续打磨。

// Веб-часть редактора AirCode: CodeMirror 6 внутри WebView.
// Связь с Kotlin: события уходят через AirCodeBridge.postMessage(JSON),
// команды приходят вызовами функций объекта window.AirCode.
import { EditorState, Compartment } from "@codemirror/state";
import {
  EditorView,
  keymap,
  lineNumbers,
  highlightActiveLine,
  highlightActiveLineGutter,
  highlightSpecialChars,
  drawSelection,
} from "@codemirror/view";
import { defaultKeymap, history, historyKeymap, indentWithTab, indentMore } from "@codemirror/commands";
import {
  StreamLanguage,
  syntaxHighlighting,
  defaultHighlightStyle,
  indentOnInput,
  bracketMatching,
  foldGutter,
  foldKeymap,
} from "@codemirror/language";
import {
  autocompletion,
  closeBrackets,
  closeBracketsKeymap,
  completeFromList,
} from "@codemirror/autocomplete";
import { SearchQuery, search, setSearchQuery, findNext, findPrevious } from "@codemirror/search";
import { oneDark } from "@codemirror/theme-one-dark";
import { javascript } from "@codemirror/lang-javascript";
import { python } from "@codemirror/lang-python";
import { java } from "@codemirror/lang-java";
import { cpp } from "@codemirror/lang-cpp";
import { csharp, objectiveC } from "@codemirror/legacy-modes/mode/clike";

// Размер порции текста при передаче между Kotlin и JS (в символах)
const CHUNK_SIZE = 100000;

const LIGHT_BACKGROUND = "#ffffff";
const DARK_BACKGROUND = "#282c34";

// Язык Cream (https://github.com/MauyaApps/cream-lang, лицензия MIT).
// Слова взяты из грамматики cream.tmLanguage.json и docs/syntax.md репозитория.
const CREAM_KEYWORDS = [
  "if", "else", "in", "repeat", "while", "try", "action", "task", "struct", "import",
  "return", "wait", "together", "say", "and", "or", "not", "for each", "on error", "or if",
];
const CREAM_CONSTANTS = ["yes", "no", "empty", "PI", "E", "INF"];
const CREAM_BUILTINS = [
  "math", "num", "rand", "stats", "convert", "regex", "list", "table", "file", "folder",
  "encode", "net", "date", "length", "sum", "min", "max", "abs", "round", "range", "sort",
  "reverse", "first", "last", "join", "split", "upper", "lower", "trim", "contains",
  "number", "bool", "input",
];
const creamWords = new Set(CREAM_KEYWORDS.filter((word) => !word.includes(" ")));
const creamConstants = new Set(CREAM_CONSTANTS);
const creamBuiltins = new Set(CREAM_BUILTINS);

const creamParser = {
  name: "cream",
  startState: () => ({ expectName: false }),
  token(stream, state) {
    if (stream.eatSpace()) return null;
    if (stream.match("--")) {
      stream.skipToEnd();
      return "comment";
    }
    if (stream.peek() === '"') {
      stream.next();
      while (!stream.eol()) {
        const ch = stream.next();
        if (ch === "\\") stream.next();
        else if (ch === '"') break;
      }
      return "string";
    }
    if (stream.match(/^(?:for each|on error|or if)\b/)) return "keyword";
    if (stream.match(/^\d+(?:\.\d+)?/)) return "number";
    if (stream.match(/^[A-Za-z_][A-Za-z0-9_]*/)) {
      const word = stream.current();
      if (state.expectName) {
        state.expectName = false;
        return "def";
      }
      if (creamWords.has(word)) {
        state.expectName = word === "action" || word === "task" || word === "struct";
        return "keyword";
      }
      if (creamConstants.has(word)) return "atom";
      if (creamBuiltins.has(word) && stream.match(/^\s*\(/, false)) return "builtin";
      return "variableName";
    }
    if (stream.match(/^(?:==|!=|>=|<=|->|→|[-+*\/%=<>|])/)) return "operator";
    stream.next();
    return null;
  },
  languageData: {
    commentTokens: { line: "--" },
    autocomplete: completeFromList([
      ...CREAM_KEYWORDS.map((label) => ({ label, type: "keyword" })),
      ...CREAM_CONSTANTS.map((label) => ({ label, type: "constant" })),
      ...CREAM_BUILTINS.map((label) => ({ label, type: "function" })),
    ]),
  },
};

// Идентификаторы языков совпадают с EditorLanguage.kt
const languages = {
  javascript: () => javascript({ jsx: true }),
  python: () => python(),
  java: () => java(),
  cpp: () => cpp(),
  csharp: () => StreamLanguage.define(csharp),
  objectivec: () => StreamLanguage.define(objectiveC),
  cream: () => StreamLanguage.define(creamParser),
};

const themeCompartment = new Compartment();
// Автодополнение включается только по разрешению пользователя (по умолчанию выключено)
const autocompleteCompartment = new Compartment();
let dark = false;
let autocompleteEnabled = false;
// Пока true, поиск открыт и Kotlin получает число совпадений
let searchActive = false;
// Максимум совпадений, которые считаем; дальше число не растёт
const SEARCH_COUNT_LIMIT = 10000;
// Пока true, изменения документа считаются программными и Kotlin о них не уведомляется
let loading = false;
// Документ, который сейчас передаётся порциями
let incoming = null;
// Состояния закрытых от просмотра вкладок (вместе с историей отмены), ключ — id вкладки
const states = new Map();
// Вкладка, показанная в редакторе сейчас; null, если ни одна
let currentId = null;

function post(message) {
  if (window.AirCodeBridge) {
    window.AirCodeBridge.postMessage(JSON.stringify(message));
  }
}

function postCursor(state) {
  const head = state.selection.main.head;
  const line = state.doc.lineAt(head);
  post({ type: "cursor", line: line.number, column: head - line.from + 1 });
}

function themeExtension() {
  return dark ? oneDark : [];
}

function autocompleteExtension() {
  return autocompleteEnabled ? autocompletion() : [];
}

// Запрос текущего поиска; null, пока поиск закрыт
let currentQuery = null;

// Считает совпадения и номер выбранного, отправляет в Kotlin
function postSearch(state) {
  let total = 0;
  let current = 0;
  if (currentQuery && currentQuery.valid) {
    const selection = state.selection.main;
    const cursor = currentQuery.getCursor(state);
    for (let next = cursor.next(); !next.done && total < SEARCH_COUNT_LIMIT; next = cursor.next()) {
      total++;
      if (next.value.from === selection.from && next.value.to === selection.to) current = total;
    }
  }
  post({ type: "search", current, total });
}

function languageExtension(id) {
  const factory = languages[id];
  return factory ? factory() : [];
}

const baseExtensions = [
  lineNumbers(),
  highlightActiveLineGutter(),
  highlightSpecialChars(),
  history(),
  foldGutter(),
  drawSelection(),
  indentOnInput(),
  syntaxHighlighting(defaultHighlightStyle, { fallback: true }),
  bracketMatching(),
  closeBrackets(),
  highlightActiveLine(),
  // Только подсветка совпадений: панель поиска рисует Compose, горячих клавиш нет
  search(),
  keymap.of([
    ...closeBracketsKeymap,
    ...defaultKeymap,
    ...historyKeymap,
    ...foldKeymap,
    indentWithTab,
  ]),
  EditorView.theme({
    "&": { height: "100%", fontSize: "14px" },
    ".cm-scroller": { overflow: "auto", fontFamily: "monospace" },
  }),
  EditorView.updateListener.of((update) => {
    if (loading) return;
    if (update.docChanged) post({ type: "changed" });
    if (update.docChanged || update.selectionSet) postCursor(update.state);
    if (searchActive && (update.docChanged || update.selectionSet)) postSearch(update.state);
  }),
];

function createState(text, language) {
  return EditorState.create({
    doc: text,
    extensions: [
      baseExtensions,
      languageExtension(language),
      themeCompartment.of(themeExtension()),
      autocompleteCompartment.of(autocompleteExtension()),
    ],
  });
}

const view = new EditorView({
  state: createState("", "plain"),
  parent: document.getElementById("editor"),
});

function applyPageBackground() {
  const color = dark ? DARK_BACKGROUND : LIGHT_BACKGROUND;
  document.documentElement.style.background = color;
  document.body.style.background = color;
}

window.AirCode = {
  // Загрузка вкладки порциями: beginOpen -> appendChunk * N -> endOpen.
  // Вкладка только запоминается, показывает её show(id)
  beginOpen(id, language) {
    incoming = { id, language, parts: [] };
  },

  appendChunk(chunk) {
    if (incoming) incoming.parts.push(chunk);
  },

  endOpen() {
    if (!incoming) return;
    const { id, language, parts } = incoming;
    incoming = null;
    states.set(id, createState(parts.join(""), language));
  },

  // Переключает редактор на вкладку, сохранив состояние текущей
  show(id) {
    const next = states.get(id);
    if (!next || currentId === id) return;
    if (currentId !== null) states.set(currentId, view.state);
    loading = true;
    try {
      view.setState(next);
      // Вкладка могла быть создана при другой теме или настройке автодополнения
      view.dispatch({
        effects: [
          themeCompartment.reconfigure(themeExtension()),
          autocompleteCompartment.reconfigure(autocompleteExtension()),
        ],
      });
    } finally {
      loading = false;
    }
    currentId = id;
    postCursor(view.state);
    view.focus();
  },

  // Забывает вкладку
  close(id) {
    states.delete(id);
    if (currentId === id) currentId = null;
  },

  setTheme(isDark) {
    dark = isDark;
    applyPageBackground();
    view.dispatch({ effects: themeCompartment.reconfigure(themeExtension()) });
  },

  setAutocomplete(enabled) {
    autocompleteEnabled = enabled;
    view.dispatch({ effects: autocompleteCompartment.reconfigure(autocompleteExtension()) });
  },

  // Поиск по файлу. Выбирает первое совпадение от курсора и шлёт {type:"search"}
  find(text, caseSensitive) {
    searchActive = true;
    currentQuery = new SearchQuery({ search: text, caseSensitive });
    view.dispatch({ effects: setSearchQuery.of(currentQuery) });
    if (currentQuery.valid) {
      // Начинаем с начала текущего выделения, чтобы при наборе не уходить вперёд
      view.dispatch({ selection: { anchor: view.state.selection.main.from } });
      findNext(view);
    }
    postSearch(view.state);
  },

  findNext() {
    if (!currentQuery || !currentQuery.valid) return;
    findNext(view);
    postSearch(view.state);
  },

  findPrevious() {
    if (!currentQuery || !currentQuery.valid) return;
    findPrevious(view);
    postSearch(view.state);
  },

  // Закрывает поиск и убирает подсветку
  clearSearch() {
    searchActive = false;
    currentQuery = null;
    view.dispatch({ effects: setSearchQuery.of(new SearchQuery({ search: "" })) });
  },

  // Вставляет текст на место выделения (панель символов)
  insertText(text) {
    view.dispatch(view.state.replaceSelection(text), {
      scrollIntoView: true,
      userEvent: "input.type",
    });
  },

  // Увеличивает отступ, как клавиша Tab (панель символов)
  indent() {
    indentMore(view);
  },

  // Отправляет текст документа в Kotlin порциями
  requestText(requestId, id) {
    const state = id === currentId ? view.state : states.get(id);
    // Неизвестная вкладка: ничего не отправляем, чтобы не записать пустой файл
    if (!state) return;
    const text = state.doc.toString();
    const total = Math.max(1, Math.ceil(text.length / CHUNK_SIZE));
    for (let index = 0; index < total; index++) {
      post({
        type: "text",
        requestId,
        index,
        total,
        data: text.slice(index * CHUNK_SIZE, (index + 1) * CHUNK_SIZE),
      });
    }
  },
};

applyPageBackground();
post({ type: "ready" });

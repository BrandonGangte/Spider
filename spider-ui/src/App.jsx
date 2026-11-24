import { Fragment, useCallback, useEffect, useRef, useState } from 'react'

const STORAGE_KEY = 'spider.conversations.v1'
const MODEL_STORAGE_KEY = 'spider.selectedModel.v1'
const SUGGESTIONS = [
  { icon: '⌘', title: 'Explain a tricky idea', prompt: 'Explain a complex idea in simple terms, with a practical example.' },
  { icon: '✳', title: 'Help me write', prompt: 'Help me write a clear, friendly message about…' },
  { icon: '</>', title: 'Think through some code', prompt: 'Help me understand this code and suggest a clean improvement.' },
]

function makeConversation() {
  return { id: crypto.randomUUID(), title: 'New conversation', messages: [], updatedAt: Date.now() }
}

function readConversations() {
  try {
    const value = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]')
    return Array.isArray(value) ? value : []
  } catch {
    return []
  }
}

function Icon({ name, size = 18 }) {
  const common = { width: size, height: size, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 1.7, strokeLinecap: 'round', strokeLinejoin: 'round', 'aria-hidden': true }
  const paths = {
    plus: <><path d="M12 5v14M5 12h14" /></>,
    send: <><path d="m22 2-7 20-4-9-9-4Z" /><path d="M22 2 11 13" /></>,
    arrow: <><path d="M7 17 17 7M7 7h10v10" /></>,
    chevron: <path d="m9 18 6-6-6-6" />,
    menu: <><path d="M4 6h16M4 12h16M4 18h16" /></>,
    sparkle: <><path d="m12 3 1.7 5.3L19 10l-5.3 1.7L12 17l-1.7-5.3L5 10l5.3-1.7L12 3Z" /><path d="m19 16 .8 2.2L22 19l-2.2.8L19 22l-.8-2.2L16 19l2.2-.8L19 16Z" /></>,
    trash: <><path d="M3 6h18M8 6V4h8v2m3 0-1 14H6L5 6m4 4v6m6-6v6" /></>,
    close: <><path d="m18 6-12 12M6 6l12 12" /></>,
    paperclip: <path d="m21.4 11.1-8.5 8.5a5.5 5.5 0 0 1-7.8-7.8l9.2-9.2a3.7 3.7 0 0 1 5.2 5.2L10.3 17a1.9 1.9 0 0 1-2.7-2.7l8.5-8.5" />,
    copy: <><rect x="8" y="8" width="13" height="13" rx="2" /><path d="M16 8V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h3" /></>,
    check: <path d="m5 12 4 4L19 6" />,
  }
  return <svg {...common}>{paths[name]}</svg>
}

function SpiderMark() {
  return <div className="brand-mark" aria-hidden="true"><SpiderIllustration className="brand-spider" /></div>
}

function SpiderIllustration({ className = '' }) {
  const legs = [
    { name: 'rear-upper', path: 'M103 79 C84 67 67 48 49 10', joints: [[80, 62], [63, 36]] },
    { name: 'mid-upper', path: 'M98 88 C76 86 59 77 37 56', joints: [[73, 84], [55, 72]] },
    { name: 'front-upper', path: 'M97 105 C72 110 54 123 30 150', joints: [[72, 112], [51, 132]] },
    { name: 'fore-upper', path: 'M103 126 C82 142 68 165 49 232', joints: [[80, 146], [65, 182]] },
    { name: 'fore-lower', path: 'M137 126 C158 142 172 165 191 232', joints: [[160, 146], [175, 182]] },
    { name: 'front-lower', path: 'M143 105 C168 110 186 123 210 150', joints: [[168, 112], [189, 132]] },
    { name: 'mid-lower', path: 'M142 88 C164 86 181 77 203 56', joints: [[167, 84], [185, 72]] },
    { name: 'rear-lower', path: 'M137 79 C156 67 173 48 191 10', joints: [[160, 62], [177, 36]] },
  ]

  return (
    <svg className={`spider-illustration ${className}`} viewBox="0 0 240 240" fill="none" aria-hidden="true">
      <g className="spider-legs">
        {legs.map((leg) => (
          <g className={`spider-leg leg-${leg.name}`} key={leg.name}>
            <path className="leg-outline" d={leg.path} />
            <path className="leg-ink" d={leg.path} />
            <path className="leg-highlight" d={leg.path} />
            {leg.joints.map(([cx, cy]) => <g key={`${cx}-${cy}`}><circle className="leg-joint-outline" cx={cx} cy={cy} r="4.2" /><circle className="leg-joint" cx={cx} cy={cy} r="2.4" /></g>)}
          </g>
        ))}
      </g>
      <g className="spider-body">
        <path className="abdomen-outline" d="M120 107c-12 2-23 9-31 21-13 20-7 45 10 61 7 7 15 12 21 15 6-3 14-8 21-15 17-16 23-41 10-61-8-12-19-19-31-21Z" />
        <path className="abdomen-shine" d="M94 133c7-15 17-23 29-25 9 0 17 3 22 9-11-4-22-2-31 4-8 5-14 12-20 22Z" />
        <path className="abdomen-seam" d="M101 127c5 13 9 25 10 41m9-58c-2 18-2 36 0 54m9-51c6 13 10 26 10 40m-47-20c11 8 20 14 30 17m-15 40c8-10 16-20 21-31" />
        <path className="abdomen-hatch" d="m96 136 7 4m-15 8 9 2m12-26 3 8m-8 37 7 6m8-46 1 8m10 27 6 6m-29 12 7-8m31-42-7 4m16 12-9 2m-3 24-7 6" />
        <path className="abdomen-spots" d="M102 143h.1m35-2h.1m-31 19h.1m27 4h.1m-15 17h.1m-18-35h.1m34 0h.1" />
        <path className="pedicel" d="M114 104c3-3 9-3 12 0l1 12c-4 3-10 3-14 0Z" />
        <path className="cephalothorax-outline" d="M120 52c-17 0-29 13-29 31s12 30 29 30 29-12 29-30-12-31-29-31Z" />
        <path className="cephalothorax-mark" d="M101 72c5-8 12-12 20-13m-20 38c5 7 12 11 20 12" />
        <circle className="spider-eye" cx="110" cy="79" r="2.1" /><circle className="spider-eye" cx="120" cy="75" r="2.1" /><circle className="spider-eye" cx="130" cy="79" r="2.1" />
        <path className="fang" d="M110 58c-4-6-7-11-7-17m27 17c4-6 7-11 7-17" />
        <path className="body-hatch" d="m108 70 5 4m-9 8 6 3m12-8 5-4m-5 17 6 3m-30 60 6 3m2 18 6 4m12-30 5-4m8 20 6 4" />
      </g>
    </svg>
  )
}

function formatTime(timestamp) {
  return new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' }).format(timestamp)
}

function CodeBlock({ language, code }) {
  const [copyState, setCopyState] = useState('idle')

  const copyCode = async () => {
    let copied = false
    try {
      await navigator.clipboard.writeText(code)
      copied = true
    } catch {
      const textarea = document.createElement('textarea')
      textarea.value = code
      textarea.setAttribute('readonly', '')
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      copied = document.execCommand('copy')
      textarea.remove()
    }
    setCopyState(copied ? 'copied' : 'failed')
    window.setTimeout(() => setCopyState('idle'), 1800)
  }

  return (
    <div className="code-block">
      <div className="code-block-header">
        <span className="code-language">{language || 'Code'}</span>
        <button className="copy-code-button" type="button" onClick={copyCode} aria-label={copyState === 'copied' ? 'Code copied' : 'Copy code'}>
          <Icon name={copyState === 'copied' ? 'check' : 'copy'} size={14} />
          <span>{copyState === 'copied' ? 'Copied' : copyState === 'failed' ? 'Copy failed' : 'Copy code'}</span>
        </button>
      </div>
      <pre><code>{code}</code></pre>
    </div>
  )
}

function renderInlineContent(content, keyPrefix) {
  const linkPattern = /\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)|(https?:\/\/[^\s<]+)/g
  const parts = []
  let lastIndex = 0
  let match
  while ((match = linkPattern.exec(content)) !== null) {
    if (match.index > lastIndex) parts.push(<Fragment key={`${keyPrefix}-text-${lastIndex}`}>{content.slice(lastIndex, match.index)}</Fragment>)
    const isMarkdownLink = Boolean(match[1])
    const rawUrl = isMarkdownLink ? match[2] : match[3]
    const url = isMarkdownLink ? rawUrl : rawUrl.replace(/[.,!?;:]+$/, '')
    const trailing = rawUrl.slice(url.length)
    parts.push(<a href={url} target="_blank" rel="noopener noreferrer" key={`${keyPrefix}-link-${match.index}`}>{isMarkdownLink ? match[1] : url}</a>)
    if (trailing) parts.push(trailing)
    lastIndex = match.index + match[0].length
  }
  if (lastIndex < content.length) parts.push(<Fragment key={`${keyPrefix}-text-${lastIndex}`}>{content.slice(lastIndex)}</Fragment>)
  return parts
}

function renderMessageContent(content) {
  const fencePattern = /```([^\r\n`]*)\r?\n([\s\S]*?)```/g
  const blocks = []
  let cursor = 0
  let match
  while ((match = fencePattern.exec(content)) !== null) {
    const prose = content.slice(cursor, match.index)
    if (prose.trim()) blocks.push(<p className="message-text" key={`text-${cursor}`}>{renderInlineContent(prose, `text-${cursor}`)}</p>)
    blocks.push(<CodeBlock language={match[1].trim()} code={match[2].replace(/\r?\n$/, '')} key={`code-${match.index}`} />)
    cursor = match.index + match[0].length
  }
  const trailing = content.slice(cursor)
  if (trailing.trim() || blocks.length === 0) {
    blocks.push(<p className="message-text" key={`text-${cursor}`}>{renderInlineContent(trailing, `text-${cursor}`)}</p>)
  }
  return blocks
}

export default function App() {
  const [conversations, setConversations] = useState(readConversations)
  const [activeId, setActiveId] = useState(() => readConversations()[0]?.id ?? null)
  const [draft, setDraft] = useState('')
  const [attachments, setAttachments] = useState([])
  const [attachmentError, setAttachmentError] = useState('')
  const [pendingConversationIds, setPendingConversationIds] = useState(() => new Set())
  const [toolActivityByConversation, setToolActivityByConversation] = useState({})
  const [turnStartedAtByConversation, setTurnStartedAtByConversation] = useState({})
  const [status, setStatus] = useState({ state: 'checking', model: 'Checking local model…' })
  const [availableModels, setAvailableModels] = useState([])
  const [selectedModel, setSelectedModel] = useState(() => localStorage.getItem(MODEL_STORAGE_KEY) || '')
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const textareaRef = useRef(null)
  const fileInputRef = useRef(null)
  const bottomRef = useRef(null)
  const pendingIdsRef = useRef(new Set())

  const activeConversation = conversations.find((conversation) => conversation.id === activeId) ?? null
  const messages = activeConversation?.messages ?? []
  const activeConversationBusy = activeId !== null && pendingConversationIds.has(activeId)
  const activeToolActivities = activeId
    ? (toolActivityByConversation[activeId] ?? []).filter((activity) => activity.startedAt >= (turnStartedAtByConversation[activeId] ?? Number.POSITIVE_INFINITY))
    : []

  const markConversationPending = (id, pending) => {
    const next = new Set(pendingIdsRef.current)
    if (pending) next.add(id)
    else next.delete(id)
    pendingIdsRef.current = next
    setPendingConversationIds(next)
  }

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(conversations))
  }, [conversations])

  useEffect(() => {
    if (selectedModel) localStorage.setItem(MODEL_STORAGE_KEY, selectedModel)
  }, [selectedModel])

  const checkStatus = useCallback(async () => {
    try {
      const response = await fetch(`/api/chat/status?model=${encodeURIComponent(selectedModel)}`)
      if (!response.ok) throw new Error('Status request failed')
      const data = await response.json()
      if (!data.ollamaReachable) {
        setStatus({ state: 'offline', model: data.model || 'Ollama' })
      } else if (!data.modelAvailable) {
        setStatus({ state: 'missing', model: data.model || 'Model' })
      } else {
        setStatus({ state: 'online', model: data.model })
      }
    } catch {
      setStatus({ state: 'offline', model: 'Ollama' })
    }
  }, [selectedModel])

  const refreshModels = useCallback(async () => {
    try {
      const response = await fetch('/api/chat/models')
      if (!response.ok) throw new Error('Model list request failed')
      const catalog = await response.json()
      const models = Array.isArray(catalog.models) ? catalog.models : []
      setAvailableModels(models)
      setSelectedModel((current) => {
        if (models.includes(current)) return current
        const fallback = models.includes(catalog.configuredModel) ? catalog.configuredModel : (models[0] || '')
        return fallback
      })
    } catch {
      setAvailableModels([])
    }
  }, [])

  const refreshToolActivity = useCallback(async (conversationId) => {
    try {
      const response = await fetch(`/api/chat/${encodeURIComponent(conversationId)}/activity`)
      if (!response.ok) return
      const activities = await response.json()
      setToolActivityByConversation((current) => ({ ...current, [conversationId]: activities }))
    } catch {
      // Tool activity is supplementary; chat can continue if polling is unavailable.
    }
  }, [])

  useEffect(() => {
    checkStatus()
    const timer = window.setInterval(checkStatus, 15000)
    return () => window.clearInterval(timer)
  }, [checkStatus])

  useEffect(() => {
    refreshModels()
    const timer = window.setInterval(refreshModels, 30000)
    return () => window.clearInterval(timer)
  }, [refreshModels])

  useEffect(() => {
    const conversationIds = [...pendingConversationIds]
    if (conversationIds.length === 0) return undefined
    const poll = () => conversationIds.forEach(refreshToolActivity)
    poll()
    const timer = window.setInterval(poll, 650)
    return () => window.clearInterval(timer)
  }, [pendingConversationIds, refreshToolActivity])

  useEffect(() => {
    const handleShortcut = (event) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        newConversation()
      }
    }
    window.addEventListener('keydown', handleShortcut)
    return () => window.removeEventListener('keydown', handleShortcut)
  }, [])

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [messages.length, activeConversationBusy])

  const newConversation = () => {
    const conversation = makeConversation()
    setConversations((current) => [conversation, ...current])
    setActiveId(conversation.id)
    setDraft('')
    setSidebarOpen(false)
    window.setTimeout(() => textareaRef.current?.focus(), 0)
  }

  const chooseConversation = (id) => {
    setActiveId(id)
    setSidebarOpen(false)
  }

  const removeConversation = (event, id) => {
    event.stopPropagation()
    setConversations((current) => current.filter((conversation) => conversation.id !== id))
    if (id === activeId) setActiveId(null)
  }

  const addFiles = (fileList) => {
    const selected = [...fileList]
    setAttachmentError('')
    if (attachments.length + selected.length > 5) {
      setAttachmentError('Attach up to five files per message.')
      return
    }
    if (attachments.filter((item) => item.file.type.startsWith('image/')).length + selected.filter((file) => file.type.startsWith('image/')).length > 3) {
      setAttachmentError('Attach up to three images per message.')
      return
    }
    const tooLarge = selected.find((file) => file.size > 8 * 1024 * 1024)
    if (tooLarge) {
      setAttachmentError(`${tooLarge.name} is larger than the 8 MB per-file limit.`)
      return
    }
    const total = attachments.reduce((sum, item) => sum + item.file.size, 0) + selected.reduce((sum, file) => sum + file.size, 0)
    if (total > 20 * 1024 * 1024) {
      setAttachmentError('Attachments must total 20 MB or less.')
      return
    }
    setAttachments((current) => [...current, ...selected.map((file) => ({
      file,
      previewUrl: file.type.startsWith('image/') ? URL.createObjectURL(file) : null,
    }))])
  }

  const removeAttachment = (index) => {
    setAttachments((current) => {
      const removed = current[index]
      if (removed?.previewUrl) URL.revokeObjectURL(removed.previewUrl)
      return current.filter((_, itemIndex) => itemIndex !== index)
    })
  }

  const sendMessage = async (rawMessage = draft) => {
    const message = rawMessage.trim()
    const outgoingAttachments = attachments
    if (!message && outgoingAttachments.length === 0) return

    let conversation = activeConversation
    if (!conversation) {
      conversation = makeConversation()
      setActiveId(conversation.id)
    }
    if (pendingIdsRef.current.has(conversation.id)) return
    setTurnStartedAtByConversation((current) => ({ ...current, [conversation.id]: Date.now() }))
    markConversationPending(conversation.id, true)

    const displayMessage = message || 'Please review these attachments.'
    const attachmentMetadata = outgoingAttachments.map(({ file }) => ({ name: file.name, type: file.type, size: file.size }))
    const userMessage = { role: 'user', content: displayMessage, attachments: attachmentMetadata, createdAt: Date.now() }
    setConversations((current) => {
      const existing = current.find((item) => item.id === conversation.id)
      const updated = {
        ...(existing || conversation),
        title: existing?.messages.length ? existing.title : (message || outgoingAttachments[0]?.file.name || 'Attached files').slice(0, 42),
        messages: [...(existing?.messages || []), userMessage],
        updatedAt: Date.now(),
      }
      return [updated, ...current.filter((item) => item.id !== conversation.id)]
    })
    setDraft('')
    if (textareaRef.current) textareaRef.current.style.height = 'auto'
    setAttachments([])
    setAttachmentError('')
    if (fileInputRef.current) fileInputRef.current.value = ''
    outgoingAttachments.forEach((item) => { if (item.previewUrl) URL.revokeObjectURL(item.previewUrl) })

    try {
      let response
      if (outgoingAttachments.length > 0) {
        const formData = new FormData()
        formData.append('conversationId', conversation.id)
        formData.append('message', message)
        if (selectedModel) formData.append('model', selectedModel)
        outgoingAttachments.forEach(({ file }) => formData.append('files', file))
        response = await fetch('/api/chat/attachments', { method: 'POST', body: formData })
      } else {
        response = await fetch('/api/chat', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ conversationId: conversation.id, message, ...(selectedModel ? { model: selectedModel } : {}) }),
        })
      }
      const data = await response.json().catch(() => ({}))
      if (!response.ok) throw new Error(data.error || 'Spider could not complete that request.')
      setConversations((current) => current.map((item) => item.id === conversation.id
        ? { ...item, messages: [...item.messages, { role: 'assistant', content: data.answer, createdAt: Date.now() }], updatedAt: Date.now() }
        : item))
      setStatus((current) => current.state === 'missing' || current.state === 'offline' ? current : { ...current, state: 'online' })
    } catch (error) {
      setConversations((current) => current.map((item) => item.id === conversation.id
        ? { ...item, messages: [...item.messages, { role: 'error', content: error.message, createdAt: Date.now() }], updatedAt: Date.now() }
        : item))
      checkStatus()
    } finally {
      await refreshToolActivity(conversation.id)
      markConversationPending(conversation.id, false)
      setTurnStartedAtByConversation((current) => {
        const next = { ...current }
        delete next[conversation.id]
        return next
      })
      window.setTimeout(() => textareaRef.current?.focus(), 0)
    }
  }

  const handleSubmit = (event) => {
    event.preventDefault()
    sendMessage()
  }

  const handleKeyDown = (event) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      sendMessage()
    }
  }

  const handlePaste = (event) => {
    const pastedImages = [...(event.clipboardData?.items ?? [])]
      .filter((item) => item.kind === 'file' && item.type.startsWith('image/'))
      .map((item) => item.getAsFile())
      .filter(Boolean)

    if (pastedImages.length === 0) return
    event.preventDefault()
    const supportedImages = pastedImages.filter((file) => ['image/png', 'image/jpeg'].includes(file.type))
    if (supportedImages.length === 0) {
      setAttachmentError('Pasted images must be PNG or JPEG.')
      return
    }
    addFiles(supportedImages.map((file, index) => {
      if (file.name) return file
      const extension = file.type === 'image/png' ? 'png' : 'jpg'
      return new File([file], `pasted-image-${Date.now()}-${index + 1}.${extension}`, { type: file.type })
    }))
    if (supportedImages.length !== pastedImages.length) setAttachmentError('Pasted images must be PNG or JPEG.')
  }

  const resizeTextarea = (event) => {
    event.currentTarget.style.height = 'auto'
    event.currentTarget.style.height = `${Math.min(event.currentTarget.scrollHeight, 180)}px`
  }

  const statusLabel = status.state === 'online'
    ? 'Model ready'
    : status.state === 'checking'
      ? 'Connecting'
      : status.state === 'missing'
        ? 'Model not found'
        : 'Ollama offline'

  return (
    <div className="app-shell">
      <div className={`mobile-scrim ${sidebarOpen ? 'visible' : ''}`} onClick={() => setSidebarOpen(false)} />
      <aside className={`sidebar ${sidebarOpen ? 'open' : ''}`}>
        <div className="sidebar-top">
          <a className="brand" href="#home" aria-label="Spider home" onClick={(event) => { event.preventDefault(); setActiveId(null) }}>
            <SpiderMark />
            <span className="brand-name">spider</span>
          </a>
          <button className="icon-button sidebar-close" aria-label="Close menu" onClick={() => setSidebarOpen(false)}><Icon name="close" /></button>
        </div>

        <button className="new-chat-button" onClick={newConversation}>
          <Icon name="plus" size={17} />
          <span>New conversation</span>
          <span className="new-shortcut">⌘ K</span>
        </button>

        <div className="sidebar-section-label"><span>Your threads</span><span className="thread-count">{conversations.length}</span></div>
        <nav className="conversation-list" aria-label="Conversations">
          {conversations.length === 0 && <p className="sidebar-empty">Your conversations will appear here.</p>}
          {conversations.map((conversation) => (
            <div className={`conversation-item ${conversation.id === activeId ? 'active' : ''}`} key={conversation.id}>
              <button className="conversation-select" onClick={() => chooseConversation(conversation.id)} title={conversation.title}>
                <span className="thread-icon"><Icon name="chevron" size={15} /></span>
                <span className="conversation-title">{conversation.title}</span>
              </button>
              <button className="delete-thread" aria-label={`Delete ${conversation.title}`} onClick={(event) => removeConversation(event, conversation.id)}><Icon name="trash" size={14} /></button>
            </div>
          ))}
        </nav>

        <div className="sidebar-bottom">
          <div className="privacy-note"><span className="privacy-lock">✳</span><div><strong>Local by design</strong><span>Your words stay on your machine.</span></div></div>
          <div className="sidebar-footer"><span className="footer-dot" /> Spider <span className="footer-version">v0.1</span><span className="footer-spacer" /><span>Made for your local model</span></div>
        </div>
      </aside>

      <main className="main-panel">
        <header className="topbar">
          <div className="topbar-left">
            <button className="icon-button menu-button" aria-label="Open menu" onClick={() => setSidebarOpen(true)}><Icon name="menu" /></button>
            <div className="breadcrumb"><span>Workspace</span><Icon name="chevron" size={13} /><strong>Chat</strong></div>
          </div>
          <div className="topbar-right">
            <div className={`model-status ${status.state}`} title={status.state === 'online' ? `${selectedModel || status.model} is available in Ollama` : status.model}>
              <span className="status-orbit"><span /></span>
              <span className="status-text">{statusLabel}</span>
            </div>
            <label className="model-chip" title="Choose an installed Ollama model">
              <span className="model-chip-icon"><Icon name="sparkle" size={15} /></span>
              <select className="model-select" aria-label="Select Ollama model" value={selectedModel} onChange={(event) => setSelectedModel(event.target.value)} disabled={availableModels.length === 0}>
                {availableModels.length === 0 && <option value="">No models found</option>}
                {availableModels.map((model) => <option value={model} key={model}>{model}</option>)}
              </select>
              <span className="local-tag">LOCAL</span>
            </label>
          </div>
        </header>

        <div className="chat-scroll-area">
          {messages.length === 0 ? (
            <section className="welcome-view" aria-label="Start a conversation">
              <div className="welcome-art" aria-hidden="true">
                <div className="web-glow" />
                <svg className="web-lines" viewBox="0 0 360 240" fill="none">
                  <path d="M180 0v240M60 35l240 170M300 35 60 205M0 120h360M38 55l284 130M322 55 38 185M90 0l180 240M270 0 90 240" />
                  <path d="M180 24c30 28 52 60 65 96-13 36-35 68-65 96-30-28-52-60-65-96 13-36 35-68 65-96Z" />
                  <path d="M180 48c22 20 37 44 46 72-9 28-24 52-46 72-22-20-37-44-46-72 9-28 24-52 46-72Z" />
                  <path d="M180 73c12 12 20 27 25 47-5 20-13 35-25 47-12-12-20-27-25-47 5-20 13-35 25-47Z" />
                </svg>
                <div className="hero-spider-crawler"><SpiderIllustration className="hero-spider" /></div>
                <span className="orbit-point point-one" /><span className="orbit-point point-two" /><span className="orbit-point point-three" />
              </div>
              <p className="eyebrow"><span className="eyebrow-line" /> YOUR PRIVATE AI WORKSPACE <span className="eyebrow-line" /></p>
              <h1>Good to have you<br /><span>in the web.</span></h1>
              <p className="welcome-copy">A thoughtful space to think, make, and figure things out.<br className="desktop-break" /> Powered by your own local model.</p>
              {status.state === 'offline' && <div className="connection-hint"><span className="hint-dot" /> Start Ollama to begin chatting. Spider will reconnect automatically.</div>}
              {status.state === 'missing' && <div className="connection-hint"><span className="hint-dot" /> Pull <code>{status.model}</code> in Ollama, or change <code>OLLAMA_MODEL</code>.</div>}
              <div className="suggestion-grid">
                {SUGGESTIONS.map((suggestion) => (
                  <button className="suggestion-card" key={suggestion.title} onClick={() => { setDraft(suggestion.prompt); textareaRef.current?.focus() }}>
                    <span className="suggestion-icon">{suggestion.icon}</span>
                    <span className="suggestion-title">{suggestion.title}</span>
                    <span className="suggestion-arrow"><Icon name="arrow" size={15} /></span>
                  </button>
                ))}
              </div>
            </section>
          ) : (
            <section className="messages-view" aria-label="Conversation">
              <div className="conversation-intro"><span className="conversation-kicker">A THREAD IN YOUR WEB</span><h2>{activeConversation?.title}</h2><p>Conversation stays on this device.</p></div>
              {messages.map((message, index) => (
                <article className={`message-row ${message.role}`} key={`${message.createdAt}-${index}`}>
                  <div className="message-avatar">{message.role === 'assistant' ? <SpiderIllustration className="avatar-spider" /> : message.role === 'error' ? '!' : 'Y'}</div>
                  <div className="message-content">
                    <div className="message-meta"><strong>{message.role === 'assistant' ? 'Spider' : message.role === 'error' ? 'Connection issue' : 'You'}</strong><time>{formatTime(message.createdAt)}</time></div>
                    <div className="message-body">{renderMessageContent(message.content)}</div>
                    {message.attachments?.length > 0 && <div className="message-attachments">{message.attachments.map((file, fileIndex) => <span className="message-attachment" key={`${file.name}-${fileIndex}`}><Icon name={file.type.startsWith('image/') ? 'sparkle' : 'paperclip'} size={13} />{file.name}</span>)}</div>}
                  </div>
                </article>
              ))}
              {activeConversationBusy && <article className="message-row assistant pending"><div className="message-avatar"><SpiderIllustration className="avatar-spider" /></div><div className="message-content"><div className="message-meta"><strong>Spider</strong><span className="thinking-label">thinking</span></div>{activeToolActivities.length > 0 && <div className="tool-activity-list">{activeToolActivities.slice(-4).map((activity) => <div className={`tool-activity ${activity.status.toLowerCase()}`} key={activity.id}><span className="tool-activity-dot" /><span>{activity.status === 'RUNNING' ? 'Using' : activity.status === 'FAILED' ? 'Could not use' : 'Used'} <strong>{activity.toolName}</strong></span></div>)}</div>}<div className="typing-indicator"><i /><i /><i /></div></div></article>}
              <div ref={bottomRef} className="scroll-anchor" />
            </section>
          )}
        </div>

        <footer className="composer-area">
          <form className="composer" onSubmit={handleSubmit}>
            <input
              ref={fileInputRef}
              className="attachment-input"
              type="file"
              multiple
              accept=".txt,.md,.csv,.json,.xml,.yaml,.yml,.html,.htm,.java,.js,.jsx,.ts,.tsx,.css,.sql,.py,.properties,.log,.toml,.ini,.sh,.ps1,.pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx,.png,.jpg,.jpeg"
              onChange={(event) => { addFiles(event.target.files); event.target.value = '' }}
              aria-label="Choose files to attach"
            />
            <textarea
              ref={textareaRef}
              value={draft}
              onChange={(event) => { setDraft(event.target.value); resizeTextarea(event) }}
              onKeyDown={handleKeyDown}
              onPaste={handlePaste}
              placeholder="Ask Spider anything or paste an image…"
              rows="1"
              aria-label="Message Spider"
              disabled={activeConversationBusy}
            />
            {attachments.length > 0 && <div className="composer-attachments">{attachments.map((attachment, index) => <div className="composer-attachment" key={`${attachment.file.name}-${index}`}>
              {attachment.previewUrl ? <img src={attachment.previewUrl} alt={`Preview of ${attachment.file.name}`} /> : <span className="attachment-file-icon"><Icon name="paperclip" size={17} /></span>}
              <span className="attachment-file-name" title={attachment.file.name}>{attachment.file.name}</span>
              <button type="button" className="remove-attachment" onClick={() => removeAttachment(index)} aria-label={`Remove ${attachment.file.name}`}><Icon name="close" size={14} /></button>
            </div>)}</div>}
            {attachmentError && <p className="attachment-error" role="alert">{attachmentError}</p>}
            <div className="composer-controls">
              <div className="composer-actions"><button className="attach-button" type="button" onClick={() => fileInputRef.current?.click()} disabled={activeConversationBusy} aria-label="Attach files" title="Attach files"><Icon name="paperclip" size={19} /></button><span className="composer-hint"><kbd>Enter</kbd> to send <span className="hint-divider">·</span> <kbd>Shift + Enter</kbd> for a new line</span></div>
              <button className="send-button" type="submit" disabled={(!draft.trim() && attachments.length === 0) || activeConversationBusy} aria-label="Send message">
                {activeConversationBusy ? <span className="send-spinner" /> : <Icon name="send" size={17} />}
              </button>
            </div>
          </form>
          <p className="composer-disclaimer">Spider can make mistakes. Your chat is processed by <span>{status.model}</span> on your machine.</p>
        </footer>
      </main>
    </div>
  )
}

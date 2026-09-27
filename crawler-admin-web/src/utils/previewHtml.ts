const encodeBase64Url = (value: string): string => {
  const bytes = new TextEncoder().encode(value)
  let binary = ''
  for (let offset = 0; offset < bytes.length; offset += 0x8000) {
    binary += String.fromCharCode(...bytes.subarray(offset, offset + 0x8000))
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

const getScriptExtension = (url: string): string => {
  const source = url.toLowerCase()
  const queryIndex = source.indexOf('?')
  const path = queryIndex > 0 ? source.slice(0, queryIndex) : source
  const dot = path.lastIndexOf('.')
  return dot >= 0 && dot < path.length - 1 ? path.slice(dot) : '.img'
}

const localResourceUrl = (url: string, type: 'css' | 'js'): string => {
  const extension = type === 'css' ? '.css' : getScriptExtension(url)
  const objectName = `${type}/${encodeBase64Url(url)}${extension}`
  return `/api/file/resource?${new URLSearchParams({ objectName }).toString()}`
}

export const resolvePreviewHtml = (
  html: string,
  baseUrl: string,
  localizeStaticResources = false
): string => {
  if (!html || !baseUrl) return html

  const container = document.createElement('div')
  container.innerHTML = html

  const toAbsolute = (source: string): string => {
    const trimmed = source.trim()
    if (!trimmed || /^(javascript:|mailto:|tel:|data:|blob:|#)/i.test(trimmed)) return trimmed
    if (/^\/api\/file\/resource(?:[/?#]|$)/i.test(trimmed)) return trimmed
    try {
      return new URL(trimmed, baseUrl).href
    } catch {
      return trimmed
    }
  }

  const resourceUrl = (source: string, type: 'css' | 'js'): string => {
    const absolute = toAbsolute(source)
    if (!localizeStaticResources || !/^https?:\/\//i.test(absolute)) return absolute
    return localResourceUrl(absolute, type)
  }

  container.querySelectorAll('a[href], img[src], source[src], video[src], audio[src]').forEach((element) => {
    const attribute = element.hasAttribute('href') ? 'href' : 'src'
    element.setAttribute(attribute, toAbsolute(element.getAttribute(attribute) || ''))
  })
  container.querySelectorAll('script[src]').forEach((script) => {
    script.setAttribute('src', resourceUrl(script.getAttribute('src') || '', 'js'))
  })
  container.querySelectorAll('link[href]').forEach((link) => {
    const isStylesheet = (link.getAttribute('rel') || '')
      .split(/\s+/)
      .some((rel) => rel.toLowerCase() === 'stylesheet')
    const href = link.getAttribute('href') || ''
    link.setAttribute('href', isStylesheet ? resourceUrl(href, 'css') : toAbsolute(href))
  })

  return container.innerHTML
}

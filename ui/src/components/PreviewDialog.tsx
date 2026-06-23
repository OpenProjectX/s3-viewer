import React, { useEffect, useMemo, useState } from 'react'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Chip from '@mui/material/Chip'
import CircularProgress from '@mui/material/CircularProgress'
import Dialog from '@mui/material/Dialog'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import IconButton from '@mui/material/IconButton'
import Stack from '@mui/material/Stack'
import ToggleButton from '@mui/material/ToggleButton'
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import CloseIcon from '@mui/icons-material/Close'
import CodeIcon from '@mui/icons-material/Code'
import FormatAlignLeftIcon from '@mui/icons-material/FormatAlignLeft'
import { Highlight, themes } from 'prism-react-renderer'
import type { Language } from 'prism-react-renderer'

import type { AvroDataPreviewResponse, AvroSchemaPreviewResponse, ParquetSchemaPreviewResponse, TextPreviewResponse } from '../types/api'
import { formatBytes } from '../utils/format'

type PreviewData =
  | { kind: 'text'; value: TextPreviewResponse }
  | { kind: 'parquet'; value: ParquetSchemaPreviewResponse }
  | { kind: 'avro-schema'; value: AvroSchemaPreviewResponse }
  | { kind: 'avro-data'; value: AvroDataPreviewResponse }

type FormatMode = 'raw' | 'pretty'

interface PreviewDialogProps {
  open: boolean
  loading: boolean
  error?: string | null
  data?: PreviewData | null
  onClose: () => void
}

function previewContent(data: PreviewData): string {
  if (data.kind === 'text' || data.kind === 'avro-data') {
    return data.value.content
  }
  return data.value.schema
}

function previewLanguage(data: PreviewData): Language {
  if (data.kind === 'parquet') {
    return 'sql'
  }
  if (data.kind === 'avro-schema' || data.kind === 'avro-data') {
    return 'json'
  }

  const fileName = data.value.fileName.toLowerCase()
  const contentType = data.value.contentType.toLowerCase()
  const extension = fileName.split('.').pop()

  if (contentType.includes('json') || extension === 'json') return 'json'
  if (contentType.includes('yaml') || contentType.includes('yml') || extension === 'yaml' || extension === 'yml') return 'yaml'
  if (contentType.includes('xml') || extension === 'xml') return 'xml'
  if (contentType.includes('html') || extension === 'html' || extension === 'htm') return 'html'
  if (contentType.includes('markdown') || extension === 'md' || extension === 'markdown') return 'markdown'
  if (extension === 'js' || extension === 'mjs' || extension === 'cjs') return 'javascript'
  if (extension === 'jsx') return 'jsx'
  if (extension === 'ts') return 'typescript'
  if (extension === 'tsx') return 'tsx'
  if (extension === 'css') return 'css'
  if (extension === 'scss' || extension === 'sass') return 'scss'
  if (extension === 'java') return 'java'
  if (extension === 'kt' || extension === 'kts') return 'kotlin'
  if (extension === 'py') return 'python'
  if (extension === 'sh' || extension === 'bash' || extension === 'zsh') return 'bash'
  if (extension === 'sql') return 'sql'
  if (extension === 'properties' || extension === 'conf' || extension === 'env') return 'properties'

  return 'text'
}

function canFormatPreview(language: Language): boolean {
  return ['json', 'xml', 'html'].includes(language)
}

function formatPreviewContent(content: string, language: Language): string {
  if (language === 'json') {
    return formatJsonContent(content)
  }
  if (language === 'xml' || language === 'html') {
    return formatMarkupContent(content)
  }
  return content
}

function formatJsonContent(content: string): string {
  try {
    return JSON.stringify(JSON.parse(content), null, 2)
  } catch {
    const lines = content.split(/\r?\n/).filter((line) => line.trim().length > 0)
    if (lines.length <= 1) {
      return content
    }

    try {
      return lines.map((line) => JSON.stringify(JSON.parse(line), null, 2)).join('\n')
    } catch {
      return content
    }
  }
}

function formatMarkupContent(content: string): string {
  const normalized = content
    .replace(/>\s+</g, '><')
    .replace(/></g, '>\n<')
    .trim()

  if (!normalized) {
    return content
  }

  let depth = 0
  return normalized
    .split('\n')
    .map((line) => {
      const trimmed = line.trim()
      if (/^<\/[^>]+>/.test(trimmed)) {
        depth = Math.max(depth - 1, 0)
      }
      const formatted = `${'  '.repeat(depth)}${trimmed}`
      if (
        /^<[^!?/][^>]*[^/]?>$/.test(trimmed) &&
        !/^<[^>]+>.*<\/[^>]+>$/.test(trimmed)
      ) {
        depth += 1
      }
      return formatted
    })
    .join('\n')
}

const PreviewDialog: React.FC<PreviewDialogProps> = ({ open, loading, error, data, onClose }) => {
  const title = data?.value.fileName ?? 'Preview'
  const [formatMode, setFormatMode] = useState<FormatMode>('pretty')
  const rawContent = data ? previewContent(data) : ''
  const language = data ? previewLanguage(data) : 'text'
  const canFormat = data ? canFormatPreview(language) : false
  const displayedContent = useMemo(
    () => (data && canFormat && formatMode === 'pretty' ? formatPreviewContent(rawContent, language) : rawContent),
    [canFormat, data, formatMode, language, rawContent]
  )

  useEffect(() => {
    setFormatMode('pretty')
  }, [rawContent])

  return (
    <Dialog open={open} onClose={onClose} maxWidth="lg" fullWidth>
      <DialogTitle sx={{ pr: 6 }}>
        <Stack direction="row" spacing={1} alignItems="center">
          <Typography variant="h6" component="span" noWrap>
            {title}
          </Typography>
          {data?.kind === 'parquet' && <Chip label="Parquet schema" size="small" />}
          {data?.kind === 'avro-schema' && <Chip label="Avro schema" size="small" />}
          {data?.kind === 'avro-data' && <Chip label="Avro data" size="small" />}
          {data?.kind === 'text' && <Chip label={data.value.contentType} size="small" />}
        </Stack>
        <IconButton
          aria-label="Close preview"
          onClick={onClose}
          sx={{ position: 'absolute', right: 8, top: 8 }}
        >
          <CloseIcon />
        </IconButton>
      </DialogTitle>
      <DialogContent dividers sx={{ minHeight: 320 }}>
        {loading && (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
            <CircularProgress />
          </Box>
        )}

        {!loading && error && <Alert severity="error">{error}</Alert>}

        {!loading && !error && data && (
          <Stack spacing={1.5}>
            <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
              {data.value.size != null && (
                <Chip label={formatBytes(data.value.size)} size="small" variant="outlined" />
              )}
              {data.kind === 'text' && data.value.truncated && (
                <Chip label="Truncated to first 1 MiB" size="small" color="warning" variant="outlined" />
              )}
              {data.kind === 'avro-data' && (
                <Chip label={`${data.value.recordCount} records`} size="small" variant="outlined" />
              )}
              {data.kind === 'avro-data' && data.value.truncated && (
                <Chip label="Truncated preview" size="small" color="warning" variant="outlined" />
              )}
              {canFormat && (
                <ToggleButtonGroup
                  size="small"
                  exclusive
                  value={formatMode}
                  onChange={(_, nextMode: FormatMode | null) => {
                    if (nextMode) {
                      setFormatMode(nextMode)
                    }
                  }}
                  sx={{ ml: 'auto' }}
                >
                  <ToggleButton value="pretty" aria-label="Pretty format">
                    <Tooltip title="Pretty format">
                      <FormatAlignLeftIcon fontSize="small" />
                    </Tooltip>
                  </ToggleButton>
                  <ToggleButton value="raw" aria-label="Raw source">
                    <Tooltip title="Raw source">
                      <CodeIcon fontSize="small" />
                    </Tooltip>
                  </ToggleButton>
                </ToggleButtonGroup>
              )}
            </Stack>
            <Highlight theme={themes.github} code={displayedContent} language={language}>
              {({ className, style, tokens, getLineProps, getTokenProps }) => (
                <Box
                  component="pre"
                  className={className}
                  sx={{
                    ...style,
                    m: 0,
                    p: 2,
                    border: '1px solid',
                    borderColor: 'divider',
                    borderRadius: 1,
                    bgcolor: '#f8fafc',
                    color: '#111827',
                    overflow: 'auto',
                    maxHeight: '65vh',
                    fontFamily: '"JetBrains Mono", "SFMono-Regular", Consolas, monospace',
                    fontSize: 13,
                    lineHeight: 1.55,
                  }}
                >
                  {tokens.map((line, lineIndex) => (
                    <Box
                      component="span"
                      key={lineIndex}
                      {...getLineProps({ line })}
                      sx={{ display: 'block', whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}
                    >
                      {line.map((token, tokenIndex) => (
                        <span key={tokenIndex} {...getTokenProps({ token })} />
                      ))}
                    </Box>
                  ))}
                </Box>
              )}
            </Highlight>
          </Stack>
        )}
      </DialogContent>
    </Dialog>
  )
}

export default PreviewDialog

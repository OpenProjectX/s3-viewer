import React from 'react'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Chip from '@mui/material/Chip'
import CircularProgress from '@mui/material/CircularProgress'
import Dialog from '@mui/material/Dialog'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import IconButton from '@mui/material/IconButton'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import CloseIcon from '@mui/icons-material/Close'

import type { ParquetSchemaPreviewResponse, TextPreviewResponse } from '../types/api'
import { formatBytes } from '../utils/format'

type PreviewData =
  | { kind: 'text'; value: TextPreviewResponse }
  | { kind: 'parquet'; value: ParquetSchemaPreviewResponse }

interface PreviewDialogProps {
  open: boolean
  loading: boolean
  error?: string | null
  data?: PreviewData | null
  onClose: () => void
}

const PreviewDialog: React.FC<PreviewDialogProps> = ({ open, loading, error, data, onClose }) => {
  const title = data?.value.fileName ?? 'Preview'

  return (
    <Dialog open={open} onClose={onClose} maxWidth="lg" fullWidth>
      <DialogTitle sx={{ pr: 6 }}>
        <Stack direction="row" spacing={1} alignItems="center">
          <Typography variant="h6" component="span" noWrap>
            {title}
          </Typography>
          {data?.kind === 'parquet' && <Chip label="Parquet schema" size="small" />}
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
            </Stack>
            <Box
              component="pre"
              sx={{
                m: 0,
                p: 2,
                borderRadius: 1,
                bgcolor: 'grey.950',
                color: 'grey.100',
                overflow: 'auto',
                maxHeight: '65vh',
                fontFamily: '"JetBrains Mono", "SFMono-Regular", Consolas, monospace',
                fontSize: 13,
                lineHeight: 1.55,
                whiteSpace: 'pre-wrap',
                wordBreak: 'break-word',
              }}
            >
              {data.kind === 'text' ? data.value.content : data.value.schema}
            </Box>
          </Stack>
        )}
      </DialogContent>
    </Dialog>
  )
}

export default PreviewDialog

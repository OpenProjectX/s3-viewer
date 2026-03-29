import React, { useCallback, useRef, useState } from 'react'
import Dialog from '@mui/material/Dialog'
import DialogTitle from '@mui/material/DialogTitle'
import DialogContent from '@mui/material/DialogContent'
import DialogActions from '@mui/material/DialogActions'
import Button from '@mui/material/Button'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import LinearProgress from '@mui/material/LinearProgress'
import List from '@mui/material/List'
import ListItem from '@mui/material/ListItem'
import ListItemIcon from '@mui/material/ListItemIcon'
import ListItemText from '@mui/material/ListItemText'
import IconButton from '@mui/material/IconButton'
import CloudUploadIcon from '@mui/icons-material/CloudUpload'
import DeleteIcon from '@mui/icons-material/Delete'
import CheckCircleIcon from '@mui/icons-material/CheckCircle'
import ErrorIcon from '@mui/icons-material/Error'
import FileIcon from './FileIcon'
import { uploadObject } from '../api'
import { formatBytes } from '../utils/format'

interface UploadDialogProps {
  open: boolean
  onClose: () => void
  providerId: string
  bucketName: string
  currentPath: string
  onUploaded: () => void
}

interface FileItem {
  file: File
  progress: number
  status: 'pending' | 'uploading' | 'done' | 'error'
  error?: string
}

const UploadDialog: React.FC<UploadDialogProps> = ({
  open,
  onClose,
  providerId,
  bucketName,
  currentPath,
  onUploaded,
}) => {
  const [files, setFiles] = useState<FileItem[]>([])
  const [uploading, setUploading] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  const handleDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    const dropped = Array.from(e.dataTransfer.files)
    addFiles(dropped)
  }, [])

  const addFiles = (newFiles: File[]) => {
    setFiles((prev) => [
      ...prev,
      ...newFiles.map((f) => ({ file: f, progress: 0, status: 'pending' as const })),
    ])
  }

  const removeFile = (index: number) => {
    setFiles((prev) => prev.filter((_, i) => i !== index))
  }

  const handleUpload = async () => {
    setUploading(true)
    let anySuccess = false

    for (let i = 0; i < files.length; i++) {
      if (files[i].status !== 'pending') continue

      setFiles((prev) =>
        prev.map((f, idx) => (idx === i ? { ...f, status: 'uploading' } : f))
      )

      try {
        await uploadObject(
          providerId,
          bucketName,
          currentPath || undefined,
          files[i].file,
          (percent) => {
            setFiles((prev) =>
              prev.map((f, idx) => (idx === i ? { ...f, progress: percent } : f))
            )
          }
        )
        setFiles((prev) =>
          prev.map((f, idx) => (idx === i ? { ...f, status: 'done', progress: 100 } : f))
        )
        anySuccess = true
      } catch (err: any) {
        setFiles((prev) =>
          prev.map((f, idx) =>
            idx === i
              ? { ...f, status: 'error', error: err?.response?.data?.message ?? 'Upload failed' }
              : f
          )
        )
      }
    }

    setUploading(false)
    if (anySuccess) onUploaded()
  }

  const handleClose = () => {
    if (!uploading) {
      setFiles([])
      onClose()
    }
  }

  const pendingCount = files.filter((f) => f.status === 'pending').length

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle>Upload Files</DialogTitle>
      <DialogContent>
        <Box
          onDrop={handleDrop}
          onDragOver={(e) => e.preventDefault()}
          onClick={() => inputRef.current?.click()}
          sx={{
            border: '2px dashed',
            borderColor: 'primary.main',
            borderRadius: 2,
            p: 4,
            textAlign: 'center',
            cursor: 'pointer',
            bgcolor: 'primary.50',
            mb: 2,
            transition: 'background 0.2s',
            '&:hover': { bgcolor: 'action.hover' },
          }}
        >
          <CloudUploadIcon sx={{ fontSize: 48, color: 'primary.main', mb: 1 }} />
          <Typography variant="body1" color="text.secondary">
            Drag & drop files here, or click to select
          </Typography>
          <input
            ref={inputRef}
            type="file"
            multiple
            hidden
            onChange={(e) => addFiles(Array.from(e.target.files ?? []))}
          />
        </Box>

        {files.length > 0 && (
          <List dense>
            {files.map((item, index) => (
              <ListItem
                key={index}
                secondaryAction={
                  item.status === 'pending' ? (
                    <IconButton edge="end" size="small" onClick={() => removeFile(index)}>
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  ) : item.status === 'done' ? (
                    <CheckCircleIcon color="success" fontSize="small" />
                  ) : item.status === 'error' ? (
                    <ErrorIcon color="error" fontSize="small" />
                  ) : null
                }
              >
                <ListItemIcon sx={{ minWidth: 36 }}>
                  <FileIcon name={item.file.name} isDirectory={false} fontSize="small" />
                </ListItemIcon>
                <ListItemText
                  primary={item.file.name}
                  secondary={
                    item.status === 'uploading'
                      ? `${item.progress}%`
                      : item.status === 'error'
                      ? item.error
                      : formatBytes(item.file.size)
                  }
                  primaryTypographyProps={{ noWrap: true }}
                />
                {item.status === 'uploading' && (
                  <Box sx={{ width: 80, ml: 1 }}>
                    <LinearProgress variant="determinate" value={item.progress} />
                  </Box>
                )}
              </ListItem>
            ))}
          </List>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} disabled={uploading}>
          Cancel
        </Button>
        <Button
          variant="contained"
          onClick={handleUpload}
          disabled={pendingCount === 0 || uploading}
        >
          Upload {pendingCount > 0 ? `(${pendingCount})` : ''}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

export default UploadDialog

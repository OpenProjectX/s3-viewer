import React, { useState } from 'react'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import TextField from '@mui/material/TextField'

import { createFolder } from '../api'

interface CreateFolderDialogProps {
  open: boolean
  onClose: () => void
  providerId: string
  bucketName: string
  currentPath: string
  onCreated: () => void
}

const CreateFolderDialog: React.FC<CreateFolderDialogProps> = ({
  open,
  onClose,
  providerId,
  bucketName,
  currentPath,
  onCreated,
}) => {
  const [folderName, setFolderName] = useState('')
  const [creating, setCreating] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const trimmedFolderName = folderName.trim().replace(/^\/+|\/+$/g, '')
  const invalid = !trimmedFolderName || trimmedFolderName.includes('/')

  const handleClose = () => {
    if (creating) return
    setFolderName('')
    setError(null)
    onClose()
  }

  const handleCreate = async () => {
    if (invalid) return

    setCreating(true)
    setError(null)
    try {
      await createFolder(providerId, bucketName, currentPath || undefined, trimmedFolderName)
      setFolderName('')
      onCreated()
      onClose()
    } catch (err: any) {
      setError(err?.response?.data?.message ?? 'Failed to create folder')
    } finally {
      setCreating(false)
    }
  }

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="xs" fullWidth>
      <DialogTitle>Create Folder</DialogTitle>
      <DialogContent sx={{ pt: 1 }}>
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
        <TextField
          autoFocus
          fullWidth
          label="Folder name"
          value={folderName}
          disabled={creating}
          error={folderName.length > 0 && invalid}
          helperText={folderName.length > 0 && invalid ? 'Use a single folder name without slashes.' : ' '}
          onChange={(event) => setFolderName(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              handleCreate()
            }
          }}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} disabled={creating}>
          Cancel
        </Button>
        <Button onClick={handleCreate} disabled={creating || invalid} variant="contained">
          Create
        </Button>
      </DialogActions>
    </Dialog>
  )
}

export default CreateFolderDialog

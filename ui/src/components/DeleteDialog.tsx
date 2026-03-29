import React, { useState } from 'react'
import Dialog from '@mui/material/Dialog'
import DialogTitle from '@mui/material/DialogTitle'
import DialogContent from '@mui/material/DialogContent'
import DialogActions from '@mui/material/DialogActions'
import DialogContentText from '@mui/material/DialogContentText'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import { deleteObjects } from '../api'

interface DeleteDialogProps {
  open: boolean
  onClose: () => void
  providerId: string
  bucketName: string
  keys: string[]
  onDeleted: () => void
}

const DeleteDialog: React.FC<DeleteDialogProps> = ({
  open,
  onClose,
  providerId,
  bucketName,
  keys,
  onDeleted,
}) => {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleDelete = async () => {
    setLoading(true)
    setError(null)
    try {
      await deleteObjects(providerId, bucketName, keys)
      onDeleted()
      onClose()
    } catch (err: any) {
      setError(err?.response?.data?.message ?? 'Delete failed')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>Delete {keys.length === 1 ? 'Object' : `${keys.length} Objects`}</DialogTitle>
      <DialogContent>
        <DialogContentText>
          {keys.length === 1
            ? `Are you sure you want to delete "${keys[0]}"? This action cannot be undone.`
            : `Are you sure you want to delete ${keys.length} selected items? This action cannot be undone.`}
        </DialogContentText>
        {error && (
          <DialogContentText color="error" sx={{ mt: 1 }}>
            {error}
          </DialogContentText>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={loading}>
          Cancel
        </Button>
        <Button
          variant="contained"
          color="error"
          onClick={handleDelete}
          disabled={loading}
          startIcon={loading ? <CircularProgress size={16} /> : undefined}
        >
          Delete
        </Button>
      </DialogActions>
    </Dialog>
  )
}

export default DeleteDialog

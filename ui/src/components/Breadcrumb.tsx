import React, { useEffect, useState } from 'react'
import Breadcrumbs from '@mui/material/Breadcrumbs'
import Link from '@mui/material/Link'
import Typography from '@mui/material/Typography'
import Box from '@mui/material/Box'
import IconButton from '@mui/material/IconButton'
import InputBase from '@mui/material/InputBase'
import Tooltip from '@mui/material/Tooltip'
import HomeIcon from '@mui/icons-material/Home'
import EditIcon from '@mui/icons-material/Edit'

interface BreadcrumbProps {
  bucketName: string
  path: string
  onNavigate: (path: string) => void
}

const Breadcrumb: React.FC<BreadcrumbProps> = ({ bucketName, path, onNavigate }) => {
  const [editing, setEditing] = useState(false)
  const [inputValue, setInputValue] = useState(path)

  // Sync input value each time editing mode opens
  useEffect(() => {
    if (editing) setInputValue(path)
  }, [editing]) // eslint-disable-line react-hooks/exhaustive-deps

  const commit = () => {
    setEditing(false)
    const normalized = inputValue.trim().replace(/^\/+|\/+$/g, '')
    onNavigate(normalized)
  }

  const cancel = () => setEditing(false)

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') commit()
    if (e.key === 'Escape') cancel()
  }

  if (editing) {
    return (
      <InputBase
        value={inputValue}
        onChange={(e) => setInputValue(e.target.value)}
        onKeyDown={handleKeyDown}
        onBlur={cancel}
        autoFocus
        fullWidth
        sx={{
          flex: 1,
          fontSize: '0.875rem',
          px: 1,
          py: 0.25,
          border: '1px solid',
          borderColor: 'primary.main',
          borderRadius: 1,
          bgcolor: 'background.paper',
        }}
        placeholder="Type a path and press Enter (e.g. folder/subfolder)"
      />
    )
  }

  const parts = path ? path.split('/').filter(Boolean) : []

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
      <Breadcrumbs aria-label="breadcrumb" sx={{ fontSize: '0.875rem', py: 0.5 }}>
        <Link
          component="button"
          underline="hover"
          color="inherit"
          onClick={() => onNavigate('')}
          sx={{ display: 'flex', alignItems: 'center', gap: 0.5, cursor: 'pointer' }}
        >
          <HomeIcon sx={{ fontSize: 16 }} />
          {bucketName}
        </Link>

        {parts.map((part, index) => {
          const isLast = index === parts.length - 1
          const segmentPath = parts.slice(0, index + 1).join('/')
          return isLast ? (
            <Typography key={segmentPath} color="text.primary" fontSize="0.875rem">
              {part}
            </Typography>
          ) : (
            <Link
              key={segmentPath}
              component="button"
              underline="hover"
              color="inherit"
              onClick={() => onNavigate(segmentPath)}
              sx={{ cursor: 'pointer' }}
            >
              {part}
            </Link>
          )
        })}
      </Breadcrumbs>

      <Tooltip title="Edit path">
        <IconButton size="small" onClick={() => setEditing(true)}>
          <EditIcon sx={{ fontSize: 14 }} />
        </IconButton>
      </Tooltip>
    </Box>
  )
}

export default Breadcrumb

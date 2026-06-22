import React, { useCallback, useEffect, useRef, useState } from 'react'
import Box from '@mui/material/Box'
import Paper from '@mui/material/Paper'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Toolbar from '@mui/material/Toolbar'
import Typography from '@mui/material/Typography'
import Button from '@mui/material/Button'
import IconButton from '@mui/material/IconButton'
import Tooltip from '@mui/material/Tooltip'
import TextField from '@mui/material/TextField'
import InputAdornment from '@mui/material/InputAdornment'
import Checkbox from '@mui/material/Checkbox'
import Chip from '@mui/material/Chip'
import CircularProgress from '@mui/material/CircularProgress'
import Alert from '@mui/material/Alert'
import ToggleButton from '@mui/material/ToggleButton'
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup'
import Grid from '@mui/material/Grid2'
import Card from '@mui/material/Card'
import CardActionArea from '@mui/material/CardActionArea'
import CardContent from '@mui/material/CardContent'

import UploadFileIcon from '@mui/icons-material/UploadFile'
import DeleteIcon from '@mui/icons-material/Delete'
import SearchIcon from '@mui/icons-material/Search'
import ClearIcon from '@mui/icons-material/Clear'
import GridViewIcon from '@mui/icons-material/GridView'
import ViewListIcon from '@mui/icons-material/ViewList'
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward'
import DownloadIcon from '@mui/icons-material/Download'

import FileIcon from './FileIcon'
import Breadcrumb from './Breadcrumb'
import UploadDialog from './UploadDialog'
import DeleteDialog from './DeleteDialog'
import { browseBucket, downloadObjectUrl, searchObjects } from '../api'
import type { ObjectEntry } from '../types/api'
import { formatBytes, formatDate } from '../utils/format'

interface FileExplorerProps {
  providerId: string
  bucketName: string
  readOnlyAccess?: boolean
}

type ViewMode = 'list' | 'grid'

const FileExplorer: React.FC<FileExplorerProps> = ({ providerId, bucketName, readOnlyAccess = false }) => {
  const [path, setPath] = useState('')
  const [entries, setEntries] = useState<ObjectEntry[]>([])
  const [parentPath, setParentPath] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [viewMode, setViewMode] = useState<ViewMode>('list')
  const [searchQuery, setSearchQuery] = useState('')
  const [searchActive, setSearchActive] = useState(false)
  const [uploadOpen, setUploadOpen] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const searchTimeout = useRef<ReturnType<typeof setTimeout> | null>(null)

  const loadDirectory = useCallback(
    async (targetPath: string) => {
      setLoading(true)
      setError(null)
      setSelected(new Set())
      try {
        const result = await browseBucket(providerId, bucketName, targetPath || undefined)
        setEntries(result.entries)
        setParentPath(result.parentPath ?? null)
        setPath(result.path)
        setSearchActive(false)
        setSearchQuery('')
      } catch (err: any) {
        setError(err?.response?.data?.message ?? 'Failed to load directory')
      } finally {
        setLoading(false)
      }
    },
    [providerId, bucketName]
  )

  useEffect(() => {
    loadDirectory('')
  }, [loadDirectory])

  const handleNavigate = (newPath: string) => {
    loadDirectory(newPath)
  }

  const handleEntryClick = (entry: ObjectEntry) => {
    if (entry.type === 'DIRECTORY') {
      handleNavigate(entry.key)
    }
  }

  const handleSearchChange = (value: string) => {
    setSearchQuery(value)
    if (searchTimeout.current) clearTimeout(searchTimeout.current)
    if (!value.trim()) {
      loadDirectory(path)
      return
    }
    searchTimeout.current = setTimeout(async () => {
      setLoading(true)
      setError(null)
      setSelected(new Set())
      try {
        const result = await searchObjects(providerId, bucketName, value, path || undefined)
        setEntries(result.entries)
        setParentPath(null)
        setSearchActive(true)
      } catch (err: any) {
        setError(err?.response?.data?.message ?? 'Search failed')
      } finally {
        setLoading(false)
      }
    }, 400)
  }

  const clearSearch = () => {
    setSearchQuery('')
    loadDirectory(path)
  }

  const toggleSelect = (key: string) => {
    setSelected((prev) => {
      const next = new Set(prev)
      if (next.has(key)) next.delete(key)
      else next.add(key)
      return next
    })
  }

  const toggleSelectAll = () => {
    if (selected.size === entries.length) {
      setSelected(new Set())
    } else {
      setSelected(new Set(entries.map((e) => e.key)))
    }
  }

  const handleDownload = (entry: ObjectEntry) => {
    const url = downloadObjectUrl(providerId, bucketName, entry.key)
    const a = document.createElement('a')
    a.href = url
    a.download = entry.name
    a.click()
  }

  const selectedKeys = Array.from(selected)

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', gap: 1 }}>
      {/* Toolbar */}
      <Paper variant="outlined" sx={{ px: 2, py: 1 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
          <Breadcrumb
            bucketName={bucketName}
            path={searchActive ? '' : path}
            onNavigate={handleNavigate}
          />
          <Box sx={{ flex: 1 }} />

          {readOnlyAccess && (
            <Chip
              label="Read-only"
              size="small"
              color="default"
              variant="outlined"
            />
          )}

          {/* Search */}
          <TextField
            size="small"
            placeholder="Search files…"
            value={searchQuery}
            onChange={(e) => handleSearchChange(e.target.value)}
            sx={{ width: 220 }}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon fontSize="small" />
                </InputAdornment>
              ),
              endAdornment: searchQuery ? (
                <InputAdornment position="end">
                  <IconButton size="small" onClick={clearSearch}>
                    <ClearIcon fontSize="small" />
                  </IconButton>
                </InputAdornment>
              ) : undefined,
            }}
          />

          {/* View mode toggle */}
          <ToggleButtonGroup
            size="small"
            value={viewMode}
            exclusive
            onChange={(_, v) => v && setViewMode(v)}
          >
            <ToggleButton value="list">
              <ViewListIcon fontSize="small" />
            </ToggleButton>
            <ToggleButton value="grid">
              <GridViewIcon fontSize="small" />
            </ToggleButton>
          </ToggleButtonGroup>

          {/* Upload */}
          {!readOnlyAccess && (
            <Button
              variant="contained"
              size="small"
              startIcon={<UploadFileIcon />}
              onClick={() => setUploadOpen(true)}
            >
              Upload
            </Button>
          )}

          {/* Delete selected */}
          {!readOnlyAccess && selected.size > 0 && (
            <Button
              variant="outlined"
              color="error"
              size="small"
              startIcon={<DeleteIcon />}
              onClick={() => setDeleteOpen(true)}
            >
              Delete ({selected.size})
            </Button>
          )}
        </Box>
      </Paper>

      {/* Status */}
      {searchActive && (
        <Chip
          label={`Search results for "${searchQuery}" — ${entries.length} found`}
          onDelete={clearSearch}
          size="small"
          sx={{ alignSelf: 'flex-start' }}
        />
      )}

      {error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}

      {/* Content */}
      {loading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 6 }}>
          <CircularProgress />
        </Box>
      ) : entries.length === 0 ? (
        <Box sx={{ textAlign: 'center', mt: 6, color: 'text.secondary' }}>
          <Typography variant="body1">This directory is empty</Typography>
        </Box>
      ) : viewMode === 'list' ? (
        <TableContainer component={Paper} variant="outlined" sx={{ flex: 1, overflow: 'auto' }}>
          <Table size="small" stickyHeader>
            <TableHead>
              <TableRow>
                {!readOnlyAccess && (
                  <TableCell padding="checkbox">
                    <Checkbox
                      size="small"
                      indeterminate={selected.size > 0 && selected.size < entries.length}
                      checked={entries.length > 0 && selected.size === entries.length}
                      onChange={toggleSelectAll}
                    />
                  </TableCell>
                )}
                <TableCell>Name</TableCell>
                <TableCell align="right">Size</TableCell>
                <TableCell align="right">Last Modified</TableCell>
                <TableCell align="center">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {/* Parent dir row */}
              {parentPath !== null && !searchActive && (
                <TableRow
                  hover
                  sx={{ cursor: 'pointer' }}
                  onClick={() => handleNavigate(parentPath)}
                >
                  {!readOnlyAccess && <TableCell padding="checkbox" />}
                  <TableCell>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      <ArrowUpwardIcon fontSize="small" sx={{ color: 'text.secondary' }} />
                      <Typography variant="body2">..</Typography>
                    </Box>
                  </TableCell>
                  <TableCell />
                  <TableCell />
                  <TableCell />
                </TableRow>
              )}

              {entries.map((entry) => (
                <TableRow
                  key={entry.key}
                  hover
                  selected={selected.has(entry.key)}
                  sx={{ cursor: entry.type === 'DIRECTORY' ? 'pointer' : 'default' }}
                >
                  {!readOnlyAccess && (
                    <TableCell padding="checkbox">
                      <Checkbox
                        size="small"
                        checked={selected.has(entry.key)}
                        onChange={() => toggleSelect(entry.key)}
                        onClick={(e) => e.stopPropagation()}
                      />
                    </TableCell>
                  )}
                  <TableCell onClick={() => handleEntryClick(entry)}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      <FileIcon
                        name={entry.name}
                        isDirectory={entry.type === 'DIRECTORY'}
                        fontSize="small"
                      />
                      <Typography
                        variant="body2"
                        sx={{
                          color: entry.type === 'DIRECTORY' ? 'primary.main' : 'text.primary',
                          fontWeight: entry.type === 'DIRECTORY' ? 500 : 400,
                        }}
                      >
                        {entry.name}
                      </Typography>
                    </Box>
                  </TableCell>
                  <TableCell align="right">
                    <Typography variant="body2" color="text.secondary">
                      {entry.type === 'DIRECTORY' ? '—' : formatBytes(entry.size)}
                    </Typography>
                  </TableCell>
                  <TableCell align="right">
                    <Typography variant="body2" color="text.secondary">
                      {formatDate(entry.lastModified)}
                    </Typography>
                  </TableCell>
                  <TableCell align="center">
                    {entry.type === 'FILE' && (
                      <Tooltip title="Download">
                        <IconButton size="small" onClick={() => handleDownload(entry)}>
                          <DownloadIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      ) : (
        /* Grid view */
        <Box sx={{ flex: 1, overflow: 'auto', p: 1 }}>
          {parentPath !== null && !searchActive && (
            <Box
              component="span"
              sx={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 0.5,
                cursor: 'pointer',
                color: 'text.secondary',
                px: 2,
                py: 1,
                mb: 1,
                borderRadius: 1,
                '&:hover': { bgcolor: 'action.hover' },
              }}
              onClick={() => handleNavigate(parentPath)}
            >
              <ArrowUpwardIcon fontSize="small" />
              <Typography variant="body2">..</Typography>
            </Box>
          )}
          <Grid container spacing={1}>
            {entries.map((entry) => (
              <Grid key={entry.key} size={{ xs: 6, sm: 4, md: 3, lg: 2 }}>
                <Card
                  variant="outlined"
                  sx={{
                    outline: selected.has(entry.key) ? '2px solid' : 'none',
                    outlineColor: 'primary.main',
                  }}
                >
                  <CardActionArea
                    onClick={() => handleEntryClick(entry)}
                    onContextMenu={(e) => {
                      e.preventDefault()
                      if (!readOnlyAccess) {
                        toggleSelect(entry.key)
                      }
                    }}
                    sx={{ p: 1.5, textAlign: 'center' }}
                  >
                    <FileIcon
                      name={entry.name}
                      isDirectory={entry.type === 'DIRECTORY'}
                      fontSize="large"
                    />
                    <CardContent sx={{ p: 0.5, pt: 1, '&:last-child': { pb: 0.5 } }}>
                      <Typography
                        variant="caption"
                        display="block"
                        noWrap
                        title={entry.name}
                        sx={{
                          fontWeight: entry.type === 'DIRECTORY' ? 500 : 400,
                        }}
                      >
                        {entry.name}
                      </Typography>
                      {entry.type === 'FILE' && (
                        <Typography variant="caption" color="text.secondary" display="block">
                          {formatBytes(entry.size)}
                        </Typography>
                      )}
                    </CardContent>
                  </CardActionArea>
                </Card>
              </Grid>
            ))}
          </Grid>
        </Box>
      )}

      {/* Dialogs */}
      <UploadDialog
        open={uploadOpen}
        onClose={() => setUploadOpen(false)}
        providerId={providerId}
        bucketName={bucketName}
        currentPath={path}
        onUploaded={() => loadDirectory(path)}
      />

      <DeleteDialog
        open={deleteOpen}
        onClose={() => setDeleteOpen(false)}
        providerId={providerId}
        bucketName={bucketName}
        keys={selectedKeys}
        onDeleted={() => {
          setSelected(new Set())
          loadDirectory(path)
        }}
      />
    </Box>
  )
}

export default FileExplorer

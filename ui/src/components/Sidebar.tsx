import React, { useEffect, useState } from 'react'
import Box from '@mui/material/Box'
import List from '@mui/material/List'
import ListItem from '@mui/material/ListItem'
import ListItemButton from '@mui/material/ListItemButton'
import ListItemIcon from '@mui/material/ListItemIcon'
import ListItemText from '@mui/material/ListItemText'
import Collapse from '@mui/material/Collapse'
import Typography from '@mui/material/Typography'
import Divider from '@mui/material/Divider'
import Skeleton from '@mui/material/Skeleton'
import StorageIcon from '@mui/icons-material/Storage'
import FolderOpenIcon from '@mui/icons-material/FolderOpen'
import ExpandLessIcon from '@mui/icons-material/ExpandLess'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import CloudIcon from '@mui/icons-material/Cloud'
import { listBuckets, listProviders } from '../api'
import type { BucketSummary, ProviderSummary } from '../types/api'

interface SidebarProps {
  selectedProvider?: string
  selectedBucket?: string
  onSelect: (providerId: string, bucketName: string) => void
}

interface ProviderNode {
  provider: ProviderSummary
  buckets: BucketSummary[]
  loading: boolean
  expanded: boolean
}

const Sidebar: React.FC<SidebarProps> = ({ selectedProvider, selectedBucket, onSelect }) => {
  const [nodes, setNodes] = useState<ProviderNode[]>([])
  const [loadingProviders, setLoadingProviders] = useState(true)

  useEffect(() => {
    listProviders()
      .then((providers) => {
        setNodes(
          providers.map((p) => ({ provider: p, buckets: [], loading: false, expanded: false }))
        )
      })
      .finally(() => setLoadingProviders(false))
  }, [])

  const toggleProvider = async (index: number) => {
    const node = nodes[index]
    if (node.expanded) {
      setNodes((prev) =>
        prev.map((n, i) => (i === index ? { ...n, expanded: false } : n))
      )
      return
    }

    setNodes((prev) =>
      prev.map((n, i) => (i === index ? { ...n, expanded: true, loading: true } : n))
    )

    try {
      const buckets = await listBuckets(node.provider.id)
      setNodes((prev) =>
        prev.map((n, i) => (i === index ? { ...n, buckets, loading: false } : n))
      )
      // Auto-select first bucket if none selected
      if (!selectedProvider && buckets.length > 0) {
        onSelect(node.provider.id, buckets[0].name)
      }
    } catch {
      setNodes((prev) =>
        prev.map((n, i) => (i === index ? { ...n, loading: false } : n))
      )
    }
  }

  return (
    <Box
      sx={{
        width: 240,
        flexShrink: 0,
        borderRight: 1,
        borderColor: 'divider',
        display: 'flex',
        flexDirection: 'column',
        bgcolor: 'background.paper',
      }}
    >
      <Box sx={{ px: 2, py: 1.5, borderBottom: 1, borderColor: 'divider' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <CloudIcon color="primary" />
          <Typography variant="subtitle2" fontWeight={700}>
            S3 Viewer
          </Typography>
        </Box>
      </Box>

      <Box sx={{ flex: 1, overflow: 'auto' }}>
        {loadingProviders ? (
          <Box sx={{ p: 2 }}>
            {[1, 2].map((i) => (
              <Skeleton key={i} height={40} sx={{ mb: 1 }} />
            ))}
          </Box>
        ) : nodes.length === 0 ? (
          <Box sx={{ p: 2 }}>
            <Typography variant="body2" color="text.secondary">
              No providers configured
            </Typography>
          </Box>
        ) : (
          <List dense disablePadding>
            {nodes.map((node, index) => (
              <React.Fragment key={node.provider.id}>
                <ListItem disablePadding>
                  <ListItemButton onClick={() => toggleProvider(index)} sx={{ py: 0.75 }}>
                    <ListItemIcon sx={{ minWidth: 32 }}>
                      <StorageIcon fontSize="small" color="action" />
                    </ListItemIcon>
                    <ListItemText
                      primary={node.provider.name}
                      secondary={node.provider.endpoint}
                      primaryTypographyProps={{ fontSize: '0.875rem', fontWeight: 600 }}
                      secondaryTypographyProps={{ fontSize: '0.7rem', noWrap: true }}
                    />
                    {node.expanded ? (
                      <ExpandLessIcon fontSize="small" />
                    ) : (
                      <ExpandMoreIcon fontSize="small" />
                    )}
                  </ListItemButton>
                </ListItem>

                <Collapse in={node.expanded} timeout="auto" unmountOnExit>
                  <List dense disablePadding>
                    {node.loading ? (
                      <Box sx={{ px: 3, py: 1 }}>
                        {[1, 2].map((i) => (
                          <Skeleton key={i} height={32} />
                        ))}
                      </Box>
                    ) : node.buckets.length === 0 ? (
                      <ListItem sx={{ pl: 4 }}>
                        <ListItemText
                          primary="No buckets"
                          primaryTypographyProps={{
                            fontSize: '0.8rem',
                            color: 'text.secondary',
                          }}
                        />
                      </ListItem>
                    ) : (
                      node.buckets.map((bucket) => {
                        const isSelected =
                          selectedProvider === node.provider.id &&
                          selectedBucket === bucket.name
                        return (
                          <ListItem key={bucket.name} disablePadding>
                            <ListItemButton
                              selected={isSelected}
                              onClick={() => onSelect(node.provider.id, bucket.name)}
                              sx={{ pl: 4, py: 0.5 }}
                            >
                              <ListItemIcon sx={{ minWidth: 28 }}>
                                <FolderOpenIcon
                                  fontSize="small"
                                  color={isSelected ? 'primary' : 'action'}
                                />
                              </ListItemIcon>
                              <ListItemText
                                primary={bucket.name}
                                primaryTypographyProps={{
                                  fontSize: '0.8rem',
                                  fontWeight: isSelected ? 600 : 400,
                                }}
                              />
                            </ListItemButton>
                          </ListItem>
                        )
                      })
                    )}
                  </List>
                </Collapse>
                <Divider />
              </React.Fragment>
            ))}
          </List>
        )}
      </Box>
    </Box>
  )
}

export default Sidebar

import React from 'react'
import Breadcrumbs from '@mui/material/Breadcrumbs'
import Link from '@mui/material/Link'
import Typography from '@mui/material/Typography'
import HomeIcon from '@mui/icons-material/Home'
import Box from '@mui/material/Box'

interface BreadcrumbProps {
  bucketName: string
  path: string
  onNavigate: (path: string) => void
}

const Breadcrumb: React.FC<BreadcrumbProps> = ({ bucketName, path, onNavigate }) => {
  const parts = path ? path.split('/').filter(Boolean) : []

  return (
    <Breadcrumbs
      aria-label="breadcrumb"
      sx={{ fontSize: '0.875rem', py: 0.5 }}
    >
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
  )
}

export default Breadcrumb

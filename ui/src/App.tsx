import React, { useState } from 'react'
import CssBaseline from '@mui/material/CssBaseline'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import { ThemeProvider } from '@mui/material/styles'
import theme from './theme'
import Sidebar from './components/Sidebar'
import FileExplorer from './components/FileExplorer'

const App: React.FC = () => {
  const [selection, setSelection] = useState<{ providerId: string; bucketName: string } | null>(
    null
  )

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
        <Sidebar
          selectedProvider={selection?.providerId}
          selectedBucket={selection?.bucketName}
          onSelect={(providerId, bucketName) => setSelection({ providerId, bucketName })}
        />

        <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden', p: 2 }}>
          {selection ? (
            <FileExplorer
              key={`${selection.providerId}/${selection.bucketName}`}
              providerId={selection.providerId}
              bucketName={selection.bucketName}
            />
          ) : (
            <Box
              sx={{
                flex: 1,
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                color: 'text.secondary',
                gap: 1,
              }}
            >
              <Typography variant="h6">Select a bucket to browse</Typography>
              <Typography variant="body2">
                Expand a provider in the sidebar and choose a bucket.
              </Typography>
            </Box>
          )}
        </Box>
      </Box>
    </ThemeProvider>
  )
}

export default App

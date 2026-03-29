import React from 'react'
import FolderIcon from '@mui/icons-material/Folder'
import ImageIcon from '@mui/icons-material/Image'
import PictureAsPdfIcon from '@mui/icons-material/PictureAsPdf'
import VideoFileIcon from '@mui/icons-material/VideoFile'
import AudioFileIcon from '@mui/icons-material/AudioFile'
import CodeIcon from '@mui/icons-material/Code'
import ArchiveIcon from '@mui/icons-material/Archive'
import InsertDriveFileIcon from '@mui/icons-material/InsertDriveFile'
import DataObjectIcon from '@mui/icons-material/DataObject'
import TableChartIcon from '@mui/icons-material/TableChart'

interface FileIconProps {
  name: string
  isDirectory: boolean
  fontSize?: 'small' | 'medium' | 'large' | 'inherit'
}

const IMAGE_EXTS = ['jpg', 'jpeg', 'png', 'gif', 'svg', 'webp', 'bmp', 'ico', 'tiff']
const VIDEO_EXTS = ['mp4', 'mkv', 'avi', 'mov', 'wmv', 'webm', 'flv']
const AUDIO_EXTS = ['mp3', 'wav', 'flac', 'aac', 'ogg', 'm4a']
const CODE_EXTS = ['js', 'ts', 'jsx', 'tsx', 'py', 'java', 'kt', 'go', 'rs', 'c', 'cpp', 'h', 'sh', 'yaml', 'yml', 'toml', 'xml', 'html', 'css', 'sql']
const ARCHIVE_EXTS = ['zip', 'tar', 'gz', 'bz2', 'xz', '7z', 'rar']
const DATA_EXTS = ['json', 'jsonl', 'ndjson', 'avro', 'parquet', 'orc', 'proto']
const TABLE_EXTS = ['csv', 'tsv', 'xls', 'xlsx']

function getExt(name: string): string {
  return name.split('.').pop()?.toLowerCase() ?? ''
}

const FileIcon: React.FC<FileIconProps> = ({ name, isDirectory, fontSize = 'medium' }) => {
  if (isDirectory) return <FolderIcon fontSize={fontSize} sx={{ color: '#f9a825' }} />

  const ext = getExt(name)

  if (ext === 'pdf') return <PictureAsPdfIcon fontSize={fontSize} sx={{ color: '#e53935' }} />
  if (IMAGE_EXTS.includes(ext)) return <ImageIcon fontSize={fontSize} sx={{ color: '#43a047' }} />
  if (VIDEO_EXTS.includes(ext)) return <VideoFileIcon fontSize={fontSize} sx={{ color: '#1e88e5' }} />
  if (AUDIO_EXTS.includes(ext)) return <AudioFileIcon fontSize={fontSize} sx={{ color: '#8e24aa' }} />
  if (CODE_EXTS.includes(ext)) return <CodeIcon fontSize={fontSize} sx={{ color: '#fb8c00' }} />
  if (ARCHIVE_EXTS.includes(ext)) return <ArchiveIcon fontSize={fontSize} sx={{ color: '#6d4c41' }} />
  if (DATA_EXTS.includes(ext)) return <DataObjectIcon fontSize={fontSize} sx={{ color: '#00897b' }} />
  if (TABLE_EXTS.includes(ext)) return <TableChartIcon fontSize={fontSize} sx={{ color: '#388e3c' }} />

  return <InsertDriveFileIcon fontSize={fontSize} sx={{ color: '#546e7a' }} />
}

export default FileIcon

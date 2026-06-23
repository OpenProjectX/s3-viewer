export interface ProviderSummary {
  id: string
  name: string
  endpoint: string
  region: string
  bucketCount: number
  pathStyleAccess?: boolean
}

export interface BucketSummary {
  name: string
  providerId: string
  objectCountHint?: number
  configured?: boolean
}

export interface BrowseResponse {
  providerId: string
  bucketName: string
  path: string
  parentPath?: string | null
  entries: ObjectEntry[]
}

export interface SearchResponse {
  providerId: string
  bucketName: string
  query: string
  entries: ObjectEntry[]
}

export interface ObjectEntry {
  name: string
  key: string
  type: 'DIRECTORY' | 'FILE'
  size?: number | null
  lastModified?: string | null
}

export interface TextPreviewResponse {
  key: string
  fileName: string
  contentType: string
  size?: number | null
  truncated: boolean
  content: string
}

export interface ParquetSchemaPreviewResponse {
  key: string
  fileName: string
  size?: number | null
  schema: string
}

export interface AvroSchemaPreviewResponse {
  key: string
  fileName: string
  size?: number | null
  schema: string
}

export interface CreateFolderRequest {
  path?: string | null
  folderName: string
}

export interface DeleteRequest {
  keys: string[]
}

import axios from 'axios'
import type {
  AvroDataPreviewResponse,
  AvroSchemaPreviewResponse,
  BrowseResponse,
  BucketSummary,
  CreateFolderRequest,
  DeleteRequest,
  ObjectEntry,
  ParquetDataPreviewResponse,
  ParquetSchemaPreviewResponse,
  ProviderSummary,
  SearchResponse,
  TextPreviewResponse,
} from '../types/api'

declare global {
  interface Window {
    __S3_VIEWER_CONFIG__?: { apiBase: string; readOnlyAccess?: boolean }
  }
}

// Resolved at runtime from config.js injected by Spring Boot.
// Falls back to the default path for local dev convenience.
const apiBase = window.__S3_VIEWER_CONFIG__?.apiBase ?? '/s3-viewer/api/v1'

export const readOnlyAccess = window.__S3_VIEWER_CONFIG__?.readOnlyAccess ?? false

const client = axios.create({ baseURL: apiBase })

export async function listProviders(): Promise<ProviderSummary[]> {
  const { data } = await client.get<ProviderSummary[]>('/providers')
  return data
}

export async function listBuckets(providerId: string): Promise<BucketSummary[]> {
  const { data } = await client.get<BucketSummary[]>(`/providers/${providerId}/buckets`)
  return data
}

export async function browseBucket(
  providerId: string,
  bucketName: string,
  path?: string
): Promise<BrowseResponse> {
  const { data } = await client.get<BrowseResponse>(
    `/providers/${providerId}/buckets/${bucketName}/browse`,
    { params: path ? { path } : undefined }
  )
  return data
}

export async function searchObjects(
  providerId: string,
  bucketName: string,
  query: string,
  path?: string,
  maxResults?: number
): Promise<SearchResponse> {
  const { data } = await client.get<SearchResponse>(
    `/providers/${providerId}/buckets/${bucketName}/search`,
    { params: { query, path, maxResults } }
  )
  return data
}

export function downloadObjectUrl(
  providerId: string,
  bucketName: string,
  key: string
): string {
  return `${apiBase}/providers/${providerId}/buckets/${bucketName}/download?key=${encodeURIComponent(key)}`
}

export async function previewTextObject(
  providerId: string,
  bucketName: string,
  key: string
): Promise<TextPreviewResponse> {
  const { data } = await client.get<TextPreviewResponse>(
    `/providers/${providerId}/buckets/${bucketName}/preview/text`,
    { params: { key } }
  )
  return data
}

export async function previewParquetSchema(
  providerId: string,
  bucketName: string,
  key: string
): Promise<ParquetSchemaPreviewResponse> {
  const { data } = await client.get<ParquetSchemaPreviewResponse>(
    `/providers/${providerId}/buckets/${bucketName}/preview/parquet/schema`,
    { params: { key } }
  )
  return data
}

export async function previewParquetData(
  providerId: string,
  bucketName: string,
  key: string,
  maxRecords = 100
): Promise<ParquetDataPreviewResponse> {
  const { data } = await client.get<ParquetDataPreviewResponse>(
    `/providers/${providerId}/buckets/${bucketName}/preview/parquet/data`,
    { params: { key, maxRecords } }
  )
  return data
}

export async function previewAvroSchema(
  providerId: string,
  bucketName: string,
  key: string
): Promise<AvroSchemaPreviewResponse> {
  const { data } = await client.get<AvroSchemaPreviewResponse>(
    `/providers/${providerId}/buckets/${bucketName}/preview/avro/schema`,
    { params: { key } }
  )
  return data
}

export async function previewAvroData(
  providerId: string,
  bucketName: string,
  key: string,
  maxRecords = 100
): Promise<AvroDataPreviewResponse> {
  const { data } = await client.get<AvroDataPreviewResponse>(
    `/providers/${providerId}/buckets/${bucketName}/preview/avro/data`,
    { params: { key, maxRecords } }
  )
  return data
}

export async function createFolder(
  providerId: string,
  bucketName: string,
  path: string | undefined,
  folderName: string
): Promise<ObjectEntry> {
  const body: CreateFolderRequest = { path, folderName }
  const { data } = await client.post<ObjectEntry>(
    `/providers/${providerId}/buckets/${bucketName}/folders`,
    body
  )
  return data
}

export async function uploadObject(
  providerId: string,
  bucketName: string,
  path: string | undefined,
  file: File,
  onProgress?: (percent: number) => void
): Promise<ObjectEntry> {
  const formData = new FormData()
  formData.append('file', file)
  const { data } = await client.post<ObjectEntry>(
    `/providers/${providerId}/buckets/${bucketName}/upload`,
    formData,
    {
      params: path ? { path } : undefined,
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (e) => {
        if (onProgress && e.total) {
          onProgress(Math.round((e.loaded * 100) / e.total))
        }
      },
    }
  )
  return data
}

export async function deleteObjects(
  providerId: string,
  bucketName: string,
  keys: string[]
): Promise<void> {
  const body: DeleteRequest = { keys }
  await client.delete(`/providers/${providerId}/buckets/${bucketName}/objects`, {
    data: body,
  })
}

import axios from 'axios'
import type {
  BrowseResponse,
  BucketSummary,
  DeleteRequest,
  ObjectEntry,
  ProviderSummary,
  SearchResponse,
} from '../types/api'

const client = axios.create({
  baseURL: '/s3-viewer/api/v1',
})

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
  return `/s3-viewer/api/v1/providers/${providerId}/buckets/${bucketName}/download?key=${encodeURIComponent(key)}`
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

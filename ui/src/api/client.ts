import type { BrowseResponse, BucketSummary, ProviderSummary } from "./types";

async function request<T>(path: string): Promise<T> {
  const response = await fetch(path);

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(errorText || `Request failed: ${response.status}`);
  }

  return response.json() as Promise<T>;
}

export function fetchProviders(): Promise<ProviderSummary[]> {
  return request("/api/v1/providers");
}

export function fetchBuckets(providerId: string): Promise<BucketSummary[]> {
  return request(`/api/v1/providers/${providerId}/buckets`);
}

export function fetchBucketContents(providerId: string, bucketName: string, path: string): Promise<BrowseResponse> {
  const params = new URLSearchParams();
  if (path) {
    params.set("path", path);
  }

  const query = params.toString();
  const suffix = query ? `?${query}` : "";

  return request(`/api/v1/providers/${providerId}/buckets/${bucketName}/browse${suffix}`);
}

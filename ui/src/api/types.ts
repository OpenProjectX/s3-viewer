export type ProviderSummary = {
  id: string;
  name: string;
  endpoint: string;
  region: string;
  bucketCount: number;
  pathStyleAccess?: boolean;
};

export type BucketSummary = {
  name: string;
  providerId: string;
  objectCountHint?: number | null;
  configured?: boolean;
};

export type ObjectEntryType = "DIRECTORY" | "FILE";

export type ObjectEntry = {
  name: string;
  key: string;
  type: ObjectEntryType;
  size?: number | null;
  lastModified?: string | null;
};

export type BrowseResponse = {
  providerId: string;
  bucketName: string;
  path: string;
  parentPath?: string | null;
  entries: ObjectEntry[];
};

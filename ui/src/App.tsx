import {
  Alert,
  Box,
  Breadcrumbs,
  Button,
  Chip,
  CircularProgress,
  Divider,
  List,
  ListItemButton,
  ListItemText,
  Paper,
  Stack,
  Typography
} from "@mui/material";
import CloudQueueRoundedIcon from "@mui/icons-material/CloudQueueRounded";
import FolderRoundedIcon from "@mui/icons-material/FolderRounded";
import InsertDriveFileRoundedIcon from "@mui/icons-material/InsertDriveFileRounded";
import LanRoundedIcon from "@mui/icons-material/LanRounded";
import StorageRoundedIcon from "@mui/icons-material/StorageRounded";
import WestRoundedIcon from "@mui/icons-material/WestRounded";
import { useEffect, useMemo, useState, type ReactNode } from "react";
import { fetchBucketContents, fetchBuckets, fetchProviders } from "./api/client";
import type { BrowseResponse, BucketSummary, ObjectEntry, ProviderSummary } from "./api/types";

function formatBytes(size?: number | null): string {
  if (size == null) return "Folder";
  const units = ["B", "KB", "MB", "GB", "TB"];
  let value = size;
  let index = 0;
  while (value >= 1024 && index < units.length - 1) {
    value /= 1024;
    index += 1;
  }
  return `${value.toFixed(value >= 10 || index === 0 ? 0 : 1)} ${units[index]}`;
}

function formatDate(value?: string | null): string {
  if (!value) return "Unknown";
  return new Intl.DateTimeFormat(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit"
  }).format(new Date(value));
}

function toSegments(path: string): string[] {
  if (!path) return [];
  return path.split("/").filter(Boolean);
}

export default function App() {
  const [providers, setProviders] = useState<ProviderSummary[]>([]);
  const [buckets, setBuckets] = useState<BucketSummary[]>([]);
  const [browseResult, setBrowseResult] = useState<BrowseResponse | null>(null);
  const [selectedProvider, setSelectedProvider] = useState<string>("");
  const [selectedBucket, setSelectedBucket] = useState<string>("");
  const [loading, setLoading] = useState(true);
  const [bucketsLoading, setBucketsLoading] = useState(false);
  const [browseLoading, setBrowseLoading] = useState(false);
  const [error, setError] = useState<string>("");

  useEffect(() => {
    let cancelled = false;

    async function loadProviders() {
      setLoading(true);
      setError("");
      try {
        const nextProviders = await fetchProviders();
        if (cancelled) return;
        setProviders(nextProviders);
        if (nextProviders[0]) {
          setSelectedProvider(nextProviders[0].id);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Failed to load providers");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void loadProviders();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!selectedProvider) {
      setBuckets([]);
      return;
    }

    let cancelled = false;

    async function loadBuckets() {
      setBucketsLoading(true);
      setError("");
      try {
        const nextBuckets = await fetchBuckets(selectedProvider);
        if (cancelled) return;
        setBuckets(nextBuckets);
        setSelectedBucket(nextBuckets[0]?.name ?? "");
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Failed to load buckets");
        }
      } finally {
        if (!cancelled) {
          setBucketsLoading(false);
        }
      }
    }

    void loadBuckets();
    return () => {
      cancelled = true;
    };
  }, [selectedProvider]);

  useEffect(() => {
    if (!selectedProvider || !selectedBucket) {
      setBrowseResult(null);
      return;
    }

    void browsePath("");
  }, [selectedProvider, selectedBucket]);

  async function browsePath(path: string) {
    setBrowseLoading(true);
    setError("");
    try {
      const nextBrowseResult = await fetchBucketContents(selectedProvider, selectedBucket, path);
      setBrowseResult(nextBrowseResult);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to browse bucket");
    } finally {
      setBrowseLoading(false);
    }
  }

  const currentProvider = useMemo(
    () => providers.find((provider) => provider.id === selectedProvider) ?? null,
    [providers, selectedProvider]
  );
  const breadcrumbs = toSegments(browseResult?.path ?? "");

  return (
    <Box
      sx={{
        minHeight: "100vh",
        background:
          "radial-gradient(circle at top left, rgba(15,118,110,0.18), transparent 32%), radial-gradient(circle at top right, rgba(249,115,22,0.16), transparent 28%), linear-gradient(180deg, #f8f3e8 0%, #f2eadc 100%)",
        px: { xs: 2, md: 4 },
        py: { xs: 3, md: 4 }
      }}
    >
      <Stack spacing={3}>
        <Paper
          sx={{
            p: { xs: 3, md: 4 },
            overflow: "hidden",
            position: "relative"
          }}
        >
          <Stack direction={{ xs: "column", md: "row" }} justifyContent="space-between" spacing={3}>
            <Box maxWidth={720}>
              <Chip
                icon={<LanRoundedIcon />}
                label="Multi-cloud S3 browser"
                sx={{
                  mb: 2,
                  backgroundColor: "rgba(15,118,110,0.10)",
                  color: "primary.main",
                  fontWeight: 700
                }}
              />
              <Typography variant="h1" sx={{ maxWidth: 680 }}>
                Inspect buckets and objects without dropping to the CLI.
              </Typography>
              <Typography sx={{ mt: 2, maxWidth: 620, color: "text.secondary", fontSize: "1.05rem" }}>
                This workspace is wired for multiple S3-compatible providers. Pick a provider, move through configured buckets,
                and browse object prefixes like a file system.
              </Typography>
            </Box>
            <Stack spacing={1.5} sx={{ minWidth: { md: 260 } }}>
              <MetricCard label="Providers" value={providers.length} icon={<CloudQueueRoundedIcon />} />
              <MetricCard label="Buckets" value={buckets.length} icon={<StorageRoundedIcon />} />
            </Stack>
          </Stack>
        </Paper>

        {error ? <Alert severity="error">{error}</Alert> : null}

        <Box
          sx={{
            display: "grid",
            gridTemplateColumns: { xs: "1fr", lg: "300px 300px minmax(0, 1fr)" },
            gap: 3,
            alignItems: "start"
          }}
        >
          <Panel title="Providers" subtitle="Configured endpoints">
            {loading ? (
              <CenteredSpinner />
            ) : (
              <List disablePadding>
                {providers.map((provider) => (
                  <ListItemButton
                    key={provider.id}
                    selected={provider.id === selectedProvider}
                    onClick={() => setSelectedProvider(provider.id)}
                    sx={{ borderRadius: 3, mb: 1 }}
                  >
                    <ListItemText
                      primary={provider.name}
                      secondary={`${provider.endpoint} • ${provider.region}`}
                      primaryTypographyProps={{ fontWeight: 700 }}
                    />
                  </ListItemButton>
                ))}
              </List>
            )}
          </Panel>

          <Panel title="Buckets" subtitle={currentProvider ? currentProvider.name : "Select a provider"}>
            {bucketsLoading ? (
              <CenteredSpinner />
            ) : (
              <List disablePadding>
                {buckets.map((bucket) => (
                  <ListItemButton
                    key={bucket.name}
                    selected={bucket.name === selectedBucket}
                    onClick={() => setSelectedBucket(bucket.name)}
                    sx={{ borderRadius: 3, mb: 1 }}
                  >
                    <ListItemText
                      primary={bucket.name}
                      secondary={bucket.configured ? "Configured bucket" : "Discovered bucket"}
                      primaryTypographyProps={{ fontWeight: 700 }}
                    />
                  </ListItemButton>
                ))}
              </List>
            )}
          </Panel>

          <Panel
            title={selectedBucket || "Objects"}
            subtitle={browseResult?.path ? `/${browseResult.path}` : "Bucket root"}
            action={
              browseResult?.parentPath !== undefined && browseResult?.parentPath !== null ? (
                <Button
                  size="small"
                  startIcon={<WestRoundedIcon />}
                  onClick={() => void browsePath(browseResult.parentPath ?? "")}
                >
                  Up
                </Button>
              ) : undefined
            }
          >
            <Stack spacing={2}>
              <Breadcrumbs separator="/" aria-label="breadcrumb">
                <Button size="small" onClick={() => void browsePath("")}>
                  root
                </Button>
                {breadcrumbs.map((segment, index) => {
                  const nextPath = breadcrumbs.slice(0, index + 1).join("/");
                  return (
                    <Button key={nextPath} size="small" onClick={() => void browsePath(nextPath)}>
                      {segment}
                    </Button>
                  );
                })}
              </Breadcrumbs>

              <Divider />

              {browseLoading ? (
                <CenteredSpinner />
              ) : (
                <Stack spacing={1.25}>
                  {(browseResult?.entries ?? []).map((entry) => (
                    <ObjectRow
                      key={`${entry.type}:${entry.key}`}
                      entry={entry}
                      onOpen={() => {
                        if (entry.type === "DIRECTORY") {
                          void browsePath(entry.key);
                        }
                      }}
                    />
                  ))}
                  {browseResult && browseResult.entries.length === 0 ? (
                    <Typography color="text.secondary">This path is empty.</Typography>
                  ) : null}
                </Stack>
              )}
            </Stack>
          </Panel>
        </Box>
      </Stack>
    </Box>
  );
}

function Panel(props: {
  title: string;
  subtitle: string;
  children: ReactNode;
  action?: ReactNode;
}) {
  return (
    <Paper sx={{ p: 2.25 }}>
      <Stack spacing={2}>
        <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={2}>
          <Box>
            <Typography variant="h3">{props.title}</Typography>
            <Typography color="text.secondary" sx={{ mt: 0.5 }}>
              {props.subtitle}
            </Typography>
          </Box>
          {props.action}
        </Stack>
        {props.children}
      </Stack>
    </Paper>
  );
}

function MetricCard(props: { label: string; value: number; icon: ReactNode }) {
  return (
    <Paper
      sx={{
        p: 2,
        border: "1px solid rgba(18,33,29,0.06)",
        background: "linear-gradient(135deg, rgba(255,250,244,0.95), rgba(250,243,233,0.95))"
      }}
    >
      <Stack direction="row" spacing={1.5} alignItems="center">
        <Box
          sx={{
            width: 42,
            height: 42,
            borderRadius: 999,
            display: "grid",
            placeItems: "center",
            backgroundColor: "rgba(15,118,110,0.10)",
            color: "primary.main"
          }}
        >
          {props.icon}
        </Box>
        <Box>
          <Typography variant="h3">{props.value}</Typography>
          <Typography color="text.secondary">{props.label}</Typography>
        </Box>
      </Stack>
    </Paper>
  );
}

function ObjectRow(props: { entry: ObjectEntry; onOpen: () => void }) {
  const isDirectory = props.entry.type === "DIRECTORY";

  return (
    <Paper
      variant="outlined"
      sx={{
        borderRadius: 4,
        borderColor: "rgba(18,33,29,0.08)",
        transition: "transform 180ms ease, box-shadow 180ms ease",
        "&:hover": {
          transform: "translateY(-1px)",
          boxShadow: "0 12px 30px rgba(18,33,29,0.08)"
        }
      }}
    >
      <ListItemButton onClick={props.onOpen} disabled={!isDirectory} sx={{ borderRadius: 4, py: 1.5 }}>
        <Stack direction="row" spacing={2} alignItems="center" sx={{ width: "100%" }}>
          <Box
            sx={{
              width: 44,
              height: 44,
              borderRadius: 3,
              display: "grid",
              placeItems: "center",
              backgroundColor: isDirectory ? "rgba(15,118,110,0.10)" : "rgba(249,115,22,0.12)",
              color: isDirectory ? "primary.main" : "secondary.main"
            }}
          >
            {isDirectory ? <FolderRoundedIcon /> : <InsertDriveFileRoundedIcon />}
          </Box>
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <Typography sx={{ fontWeight: 700 }} noWrap>
              {props.entry.name}
            </Typography>
            <Typography color="text.secondary" sx={{ fontSize: "0.9rem" }} noWrap>
              {props.entry.key || "/"}
            </Typography>
          </Box>
          <Box sx={{ textAlign: "right" }}>
            <Typography sx={{ fontWeight: 700 }}>{formatBytes(props.entry.size)}</Typography>
            <Typography color="text.secondary" sx={{ fontSize: "0.9rem" }}>
              {formatDate(props.entry.lastModified)}
            </Typography>
          </Box>
        </Stack>
      </ListItemButton>
    </Paper>
  );
}

function CenteredSpinner() {
  return (
    <Stack direction="row" justifyContent="center" sx={{ py: 4 }}>
      <CircularProgress />
    </Stack>
  );
}

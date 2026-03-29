#!/bin/bash
set -e

echo "Creating S3 buckets..."
awslocal s3 mb s3://demo-assets
awslocal s3 mb s3://demo-archive
echo "Buckets created: demo-assets, demo-archive"

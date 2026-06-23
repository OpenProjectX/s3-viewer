CREATE TABLE IF NOT EXISTS prep_s3.demo.objects (
    id BIGINT,
    bucket STRING,
    object_key STRING
) USING iceberg;

INSERT INTO prep_s3.demo.objects VALUES
    (1, 'spark-sql-prep-s3', 'warehouse/demo/object-1.txt');

SELECT COUNT(*) AS object_count FROM prep_s3.demo.objects;

-- Nganya Experience - Production DB Fix / Migration
-- Safe to run multiple times.

-- 1) Ensure bookings has expected columns
ALTER TABLE IF EXISTS bookings
    ADD COLUMN IF NOT EXISTS payment_status VARCHAR(20) DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS ticket_code VARCHAR(64);

-- 2) Ensure mpesa_payments has expected columns
ALTER TABLE IF EXISTS mpesa_payments
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS raw_callback TEXT,
    ADD COLUMN IF NOT EXISTS mpesa_receipt VARCHAR(64),
    ADD COLUMN IF NOT EXISTS merchant_request_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS checkout_request_id VARCHAR(128);

-- Unique checkout id (Daraja sends this back on callback)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes WHERE indexname = 'ux_mpesa_payments_checkout_request_id'
    ) THEN
        CREATE UNIQUE INDEX ux_mpesa_payments_checkout_request_id ON mpesa_payments (checkout_request_id);
    END IF;
END $$;

-- One payment per booking
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes WHERE indexname = 'ux_mpesa_payments_booking_id'
    ) THEN
        CREATE UNIQUE INDEX ux_mpesa_payments_booking_id ON mpesa_payments (booking_id);
    END IF;
END $$;

-- 3) Normalize null statuses
UPDATE bookings SET payment_status = 'PENDING' WHERE payment_status IS NULL;
UPDATE mpesa_payments SET status = 'PENDING' WHERE status IS NULL;

-- 4) Fix ticket_types sold counters
-- IMPORTANT: sold should reflect ONLY PAID bookings (not PENDING)
UPDATE ticket_types tt
SET sold = COALESCE(
    (
        SELECT COUNT(*)
        FROM bookings b
        WHERE b.ticket_type_id = tt.id
          AND UPPER(COALESCE(b.payment_status, 'PENDING')) = 'PAID'
    ),
    0
);

-- Ensure non-null
UPDATE ticket_types SET sold = 0 WHERE sold IS NULL;
UPDATE ticket_types SET capacity = 0 WHERE capacity IS NULL;

-- 5) Optional: mark orphan mpesa payments as FAILED
-- (payments without a booking should not exist, but just in case)
UPDATE mpesa_payments p
SET status = 'FAILED'
WHERE booking_id IS NULL AND status = 'PENDING';

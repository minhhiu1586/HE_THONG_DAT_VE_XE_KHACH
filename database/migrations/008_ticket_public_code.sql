-- Human-readable ticket references; internal ticket/QR IDs remain stable.
CREATE SEQUENCE ticket_public_code_seq MINVALUE 1 MAXVALUE 33554431 NO CYCLE;
CREATE FUNCTION next_ticket_public_code() RETURNS varchar(5) LANGUAGE plpgsql AS $$
DECLARE
  value bigint := (nextval('ticket_public_code_seq') * 2654435761) & 33554431;
  alphabet text := '0123456789ABCDEFGHJKMNPQRSTVWXYZ';
  code text := '';
BEGIN
  FOR i IN 1..5 LOOP
    code := substr(alphabet, (value & 31)::integer + 1, 1) || code;
    value := value >> 5;
  END LOOP;
  RETURN code;
END;
$$;
ALTER TABLE ve_xe ADD COLUMN ma_tra_cuu varchar(5) DEFAULT next_ticket_public_code();
ALTER TABLE ve_xe ALTER COLUMN ma_tra_cuu SET NOT NULL;
ALTER TABLE ve_xe ADD CONSTRAINT uq_ve_xe_ma_tra_cuu UNIQUE (ma_tra_cuu);

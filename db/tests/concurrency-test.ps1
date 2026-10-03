# Prueba de concurrencia real: dos sesiones PostgreSQL simultáneas sobre una base temporal.
# Uso: .\db\tests\concurrency-test.ps1   (requiere el contenedor bancoias-postgres en ejecución)
$ErrorActionPreference = 'Stop'
$container = 'bancoias-postgres'
$testDb = 'bancoias_concurrency_test'

function Invoke-Psql([string]$Database, [string]$Sql) {
    "SET client_min_messages = warning;`n$Sql" | docker exec -i $container sh -c "psql -v ON_ERROR_STOP=1 -q -t -A -U `"`$POSTGRES_USER`" -d $Database"
    if ($LASTEXITCODE -ne 0) { throw "psql fallo en $Database" }
}

function Reset-TestDatabase {
    Invoke-Psql 'postgres' "DROP DATABASE IF EXISTS $testDb WITH (FORCE);"
    Invoke-Psql 'postgres' "CREATE DATABASE $testDb;"
    docker exec $container sh -c "for f in /docker-entrypoint-initdb.d/*.sql; do psql -v ON_ERROR_STOP=1 -q -U `"`$POSTGRES_USER`" -d $testDb -f `$f || exit 1; done"
    if ($LASTEXITCODE -ne 0) { throw 'No fue posible aplicar las migraciones' }
}

# pg_sleep mantiene el lock de fila para forzar que la segunda sesión espere y re-evalúe el WHERE.
$processSql = @'
DO $$
DECLARE v_rows INT;
BEGIN
    UPDATE customer.customers
       SET current_approved_amount = current_approved_amount + {amount},
           updated_at = CURRENT_TIMESTAMP
     WHERE customer_id = '{customer}'
       AND status = 'ELIGIBLE'
       AND current_approved_amount + {amount} <= max_approved_amount;
    GET DIAGNOSTICS v_rows = ROW_COUNT;
    PERFORM pg_sleep(1);
    INSERT INTO credit.credit_applications
        (application_reference, customer_id, amount, term_months, status, decision_reason, processed_at)
    VALUES ('{ref}', '{customer}', {amount}, 24,
            CASE WHEN v_rows = 1 THEN 'APPROVED' ELSE 'REJECTED' END,
            CASE WHEN v_rows = 1 THEN 'CREDIT_LIMIT_AVAILABLE' ELSE 'CREDIT_LIMIT_EXCEEDED' END,
            clock_timestamp());
END $$;
'@

function Invoke-Parallel([object[]]$Requests) {
    $jobs = foreach ($r in $Requests) {
        $sql = $processSql.Replace('{ref}', $r.Ref).Replace('{customer}', $r.Customer).Replace('{amount}', [string]$r.Amount)
        Start-Job -ArgumentList $container, $testDb, $sql -ScriptBlock {
            param($c, $db, $s)
            $ErrorActionPreference = 'Continue'
            $s | docker exec -i $c sh -c "psql -v ON_ERROR_STOP=1 -q -U `"`$POSTGRES_USER`" -d $db" 2>&1 | Out-Null
        }
    }
    $jobs | Wait-Job | Remove-Job
}

function Assert-Equal($Actual, $Expected, [string]$Message) {
    if ("$Actual".Trim() -ne "$Expected") { throw "FALLO: $Message. Esperado=$Expected Actual=$Actual" }
    Write-Host "  OK: $Message ($Expected)"
}

function Get-Scalar([string]$Sql) { (Invoke-Psql $testDb $Sql | Out-String).Trim() }

try {
    Write-Host 'Escenario 1: CLI-1001 (cupo 10M) recibe 6M + 6M simultaneos'
    Reset-TestDatabase
    Invoke-Parallel @(@{ Ref = 'C-A'; Customer = 'CLI-1001'; Amount = 6000000 }, @{ Ref = 'C-B'; Customer = 'CLI-1001'; Amount = 6000000 })
    Assert-Equal (Get-Scalar "SELECT current_approved_amount::BIGINT FROM customer.customers WHERE customer_id='CLI-1001'") 6000000 'Cupo consumido'
    Assert-Equal (Get-Scalar "SELECT count(*) FROM credit.credit_applications WHERE status='APPROVED'") 1 'Aprobadas'
    Assert-Equal (Get-Scalar "SELECT count(*) FROM credit.credit_applications WHERE status='REJECTED'") 1 'Rechazadas'

    Write-Host 'Escenario 2: CLI-1001 (cupo 10M) recibe 6M + 4M simultaneos'
    Reset-TestDatabase
    Invoke-Parallel @(@{ Ref = 'C-A'; Customer = 'CLI-1001'; Amount = 6000000 }, @{ Ref = 'C-B'; Customer = 'CLI-1001'; Amount = 4000000 })
    Assert-Equal (Get-Scalar "SELECT current_approved_amount::BIGINT FROM customer.customers WHERE customer_id='CLI-1001'") 10000000 'Cupo consumido'
    Assert-Equal (Get-Scalar "SELECT count(*) FROM credit.credit_applications WHERE status='APPROVED'") 2 'Aprobadas'

    Write-Host 'Escenario 3: misma referencia REF-DUP enviada dos veces simultaneamente (CLI-2001, 6M)'
    Reset-TestDatabase
    Invoke-Parallel @(@{ Ref = 'REF-DUP'; Customer = 'CLI-2001'; Amount = 6000000 }, @{ Ref = 'REF-DUP'; Customer = 'CLI-2001'; Amount = 6000000 })
    Assert-Equal (Get-Scalar "SELECT current_approved_amount::BIGINT FROM customer.customers WHERE customer_id='CLI-2001'") 6000000 'Cupo consumido una sola vez'
    Assert-Equal (Get-Scalar "SELECT count(*) FROM credit.credit_applications WHERE application_reference='REF-DUP'") 1 'Solicitudes con REF-DUP'

    Write-Host 'OK: pruebas de concurrencia superadas'
}
finally {
    Invoke-Psql 'postgres' "DROP DATABASE IF EXISTS $testDb WITH (FORCE);"
}

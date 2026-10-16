import { expect, test } from '@playwright/test';

interface Transaction {
    id: string;
    merchant: string;
    reference: string;
    direction: string;
    status: string;
    dispute: { id: string | null; eligible: boolean };
}

interface TransactionEnvelope {
    data?: { items?: Transaction[] };
}

test('customer submits an eligible transaction dispute and sees it in history', async ({ page }) => {
    const email = process.env.E2E_CUSTOMER_EMAIL;
    const password = process.env.E2E_CUSTOMER_PASSWORD;
    expect(email, 'E2E_CUSTOMER_EMAIL must be set').toBeTruthy();
    expect(password, 'E2E_CUSTOMER_PASSWORD must be set').toBeTruthy();

    await page.goto('/login');
    await page.getByLabel('Email').fill(email!);
    await page.getByRole('textbox', { name: 'Password' }).fill(password!);
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/\/customer/);

    await page.goto('/customer/transactions?size=100');
    await expect(page.getByRole('heading', { name: 'Transactions' })).toBeVisible();
    const response = await page.request.get('/api/v1/transactions?size=100');
    expect(response.ok()).toBeTruthy();
    const envelope = (await response.json()) as TransactionEnvelope;
    const transaction = envelope.data?.items?.find(
        (item) =>
            item.direction === 'OUT' &&
            item.status === 'COMPLETED' &&
            item.dispute.eligible &&
            item.dispute.id === null,
    );
    expect(transaction, 'the customer fixture must contain an eligible transaction').toBeTruthy();

    const row = page
        .locator('tr.transaction-row')
        .filter({ hasText: transaction!.merchant })
        .filter({ hasText: transaction!.reference });
    await expect(row).toBeVisible();
    await row.getByRole('button', { name: `Expand ${transaction!.merchant} transaction` }).click();
    const expansion = row.locator('xpath=following-sibling::tr[1]');
    await expansion.getByRole('button', { name: /Raise dispute/ }).click();

    await expect(page).toHaveURL(/\/customer\/disputes\/new\?transactionId=/);
    await page.getByLabel('Reason for dispute').selectOption('DUPLICATE_CHARGE');
    await page.getByLabel('What happened?').fill('I do not recognise this synthetic transaction.');
    await page.getByRole('button', { name: 'Submit dispute' }).click();
    await expect(page.getByRole('heading', { name: 'Your case is open' })).toBeVisible();

    await page.goto('/customer/disputes');
    await expect(page.getByRole('heading', { name: 'Your disputes' })).toBeVisible();
    await expect(page.getByText(transaction!.merchant).first()).toBeVisible();
});

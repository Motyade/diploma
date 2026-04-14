import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8180';

export const options = {
    stages: [
        { duration: '30s', target: 100 },
        { duration: '2m', target: 100 },
        { duration: '30s', target: 0 },
    ],
    thresholds: {
        http_req_duration: ['p(95)<500'],
        http_req_failed: ['rate<0.01'],
    },
};

export function setup() {
    const loginRes = http.post(`${BASE_URL}/api/v1/auth/login`,
        JSON.stringify({ phone_number: '+70001111111', password: 'password' }),
        { headers: { 'Content-Type': 'application/json' } }
    );
    if (loginRes.status !== 200) {
        console.error('Setup login failed');
        return {};
    }
    return { accessToken: loginRes.json('access_token') };
}

export default function (data) {
    const headers = data.accessToken
        ? { 'Authorization': `Bearer ${data.accessToken}` }
        : {};

    const dashboardRes = http.get(
        `${BASE_URL}/api/v1/analytics/dashboard?period=today`,
        { headers }
    );
    check(dashboardRes, { 'dashboard status 200': (r) => r.status === 200 });

    const now = new Date().toISOString();
    const weekAgo = new Date(Date.now() - 7 * 86400000).toISOString();
    const consultantsRes = http.get(
        `${BASE_URL}/api/v1/analytics/consultants?dateFrom=${weekAgo}&dateTo=${now}`,
        { headers }
    );
    check(consultantsRes, { 'consultants status 200': (r) => r.status === 200 });

    const requestsRes = http.get(
        `${BASE_URL}/api/v1/analytics/requests?page=0&size=20`,
        { headers }
    );
    check(requestsRes, { 'requests status 200': (r) => r.status === 200 });

    sleep(1);
}

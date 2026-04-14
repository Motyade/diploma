import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8180';
const QR_TOKEN = __ENV.QR_TOKEN || '00000000-0000-0000-0000-000000000001';
const READ_USER_ID = __ENV.READ_USER_ID || '11111111-1111-1111-1111-111111111111';
const READ_ROLE = __ENV.READ_ROLE || 'MANAGER';

const readOps = new Counter('read_operations');
const writeOps = new Counter('write_operations');
const analyticsOps = new Counter('analytics_operations');
const readDuration = new Trend('read_duration');
const writeDuration = new Trend('write_duration');
const analyticsDuration = new Trend('analytics_duration');

export const options = {
    stages: [
        { duration: '30s', target: 50 },
        { duration: '3m', target: 50 },
        { duration: '30s', target: 0 },
    ],
    thresholds: {
        http_req_duration: ['p(95)<800'],
        http_req_failed: ['rate<0.05'],
        read_duration: ['p(95)<400'],
        write_duration: ['p(95)<1000'],
        analytics_duration: ['p(95)<600'],
    },
};

export function setup() {
    const loginRes = http.post(`${BASE_URL}/api/v1/auth/login`,
        JSON.stringify({ phone_number: '+70001111111', password: 'password' }),
        { headers: { 'Content-Type': 'application/json' } }
    );
    return loginRes.status === 200
        ? { accessToken: loginRes.json('access_token') }
        : {};
}

function doRead(data) {
    const headers = data.accessToken
        ? {
            'Authorization': `Bearer ${data.accessToken}`,
            'X-User-Id': READ_USER_ID,
            'X-Role': READ_ROLE,
        }
        : {
            'X-User-Id': READ_USER_ID,
            'X-Role': READ_ROLE,
        };

    const start = Date.now();
    const res = http.get(`${BASE_URL}/api/v1/requests?page=0&size=10`, { headers });
    readDuration.add(Date.now() - start);
    readOps.add(1);
    check(res, { 'read ok': (r) => r.status === 200 });
}

function doWrite() {
    const start = Date.now();
    const res = http.post(`${BASE_URL}/api/v1/requests`,
        JSON.stringify({ qr_token: QR_TOKEN }),
        { headers: { 'Content-Type': 'application/json' } }
    );
    writeDuration.add(Date.now() - start);
    writeOps.add(1);
    check(res, { 'write ok': (r) => r.status === 201 || r.status === 200 });
}

function doAnalytics(data) {
    const headers = data.accessToken
        ? { 'Authorization': `Bearer ${data.accessToken}` }
        : {};

    const start = Date.now();
    const res = http.get(`${BASE_URL}/api/v1/analytics/dashboard?period=today`, { headers });
    analyticsDuration.add(Date.now() - start);
    analyticsOps.add(1);
    check(res, { 'analytics ok': (r) => r.status === 200 });
}

export default function (data) {
    const rand = Math.random();

    if (rand < 0.6) {
        doRead(data);
    } else if (rand < 0.9) {
        doWrite();
    } else {
        doAnalytics(data);
    }

    sleep(0.5 + Math.random());
}

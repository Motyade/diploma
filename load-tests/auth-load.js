import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8180';

export const options = {
    stages: [
        { duration: '30s', target: 50 },
        { duration: '2m', target: 50 },
        { duration: '30s', target: 0 },
    ],
    thresholds: {
        http_req_duration: ['p(95)<800'],
        http_req_failed: ['rate<0.01'],
    },
};

export function setup() {
    const loginRes = http.post(`${BASE_URL}/api/v1/auth/login`,
        JSON.stringify({ phone_number: '+70001111111', password: 'password' }),
        { headers: { 'Content-Type': 'application/json' } }
    );
    if (loginRes.status !== 200) {
        console.error('Setup login failed:', loginRes.status, loginRes.body);
        return {};
    }
    return {
        accessToken: loginRes.json('access_token'),
        refreshToken: loginRes.json('refresh_token'),
    };
}

export default function (data) {
    const loginRes = http.post(`${BASE_URL}/api/v1/auth/login`,
        JSON.stringify({ phone_number: '+70001111111', password: 'password' }),
        { headers: { 'Content-Type': 'application/json' } }
    );
    check(loginRes, { 'login status 200': (r) => r.status === 200 });

    if (data.refreshToken) {
        const refreshRes = http.post(`${BASE_URL}/api/v1/auth/refresh`,
            JSON.stringify({ refresh_token: data.refreshToken }),
            { headers: { 'Content-Type': 'application/json' } }
        );
        check(refreshRes, { 'refresh status 200': (r) => r.status === 200 });
    }

    if (data.accessToken) {
        const meRes = http.get(`${BASE_URL}/api/v1/auth/me`, {
            headers: { 'Authorization': `Bearer ${data.accessToken}` },
        });
        check(meRes, { 'me status 200': (r) => r.status === 200 });
    }

    sleep(1);
}

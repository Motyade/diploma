import http from 'k6/http';
import { check, sleep, group } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8180';
const QR_TOKEN = __ENV.QR_TOKEN || '00000000-0000-0000-0000-000000000001';

export const options = {
    stages: [
        { duration: '30s', target: 30 },
        { duration: '2m', target: 30 },
        { duration: '30s', target: 0 },
    ],
    thresholds: {
        http_req_duration: ['p(95)<1000'],
        http_req_failed: ['rate<0.05'],
    },
};

export function setup() {
    const mgrLogin = http.post(`${BASE_URL}/api/v1/auth/login`,
        JSON.stringify({ phone_number: '+70001111111', password: 'password' }),
        { headers: { 'Content-Type': 'application/json' } }
    );

    const conLogin = http.post(`${BASE_URL}/api/v1/auth/login`,
        JSON.stringify({ phone_number: '+70002222222', password: 'password' }),
        { headers: { 'Content-Type': 'application/json' } }
    );

    return {
        managerToken: mgrLogin.status === 200 ? mgrLogin.json('access_token') : null,
        consultantToken: conLogin.status === 200 ? conLogin.json('access_token') : null,
    };
}

export default function (data) {
    group('Create Request', () => {
        const createRes = http.post(`${BASE_URL}/api/v1/requests`,
            JSON.stringify({ qr_token: QR_TOKEN }),
            { headers: { 'Content-Type': 'application/json' } }
        );
        check(createRes, { 'create status 201': (r) => r.status === 201 });

        if (createRes.status === 201) {
            const request = createRes.json();
            const requestId = request.id;
            const session = request.client_session_token;

            group('Get Request', () => {
                const getRes = http.get(
                    `${BASE_URL}/api/v1/requests/${requestId}?session=${session}`
                );
                check(getRes, { 'get status 200': (r) => r.status === 200 });
            });

            if (data.consultantToken) {
                group('Assign Request', () => {
                    const assignRes = http.post(
                        `${BASE_URL}/api/v1/requests/${requestId}/assign`,
                        null,
                        { headers: { 'Authorization': `Bearer ${data.consultantToken}` } }
                    );
                    check(assignRes, {
                        'assign status 200': (r) => r.status === 200,
                    });

                    if (assignRes.status === 200) {
                        group('Complete Request', () => {
                            const completeRes = http.post(
                                `${BASE_URL}/api/v1/requests/${requestId}/complete`,
                                null,
                                { headers: { 'Authorization': `Bearer ${data.consultantToken}` } }
                            );
                            check(completeRes, {
                                'complete status 200': (r) => r.status === 200,
                            });
                        });
                    }
                });
            }
        }
    });

    sleep(2);
}

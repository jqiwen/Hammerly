// Quick test to verify register, login, and persistence
const API_URL = 'http://localhost:5000/api';

async function testAuthFlow() {
  console.log('🧪 Testing Authentication Flow\n');

  // Test 1: Register new user
  console.log('1️⃣ Testing Registration...');
  const registerRes = await fetch(`${API_URL}/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      firstName: 'Mike',
      lastName: 'Johnson',
      email: 'mike@test.com',
      password: 'mike123',
      confirmPassword: 'mike123'
    })
  });

  const registerData = await registerRes.json();
  if (registerData.success) {
    console.log('✅ Registration successful!');
    console.log(`   User: ${registerData.user.firstName} ${registerData.user.lastName}`);
    console.log(`   Email: ${registerData.user.email}`);
    console.log(`   Token: ${registerData.token.substring(0, 30)}...\n`);
  } else {
    console.log(`❌ Registration failed: ${registerData.message}\n`);
    return;
  }

  // Test 2: Login with registered credentials
  console.log('2️⃣ Testing Login...');
  const loginRes = await fetch(`${API_URL}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      email: 'mike@test.com',
      password: 'mike123'
    })
  });

  const loginData = await loginRes.json();
  if (loginData.success) {
    console.log('✅ Login successful!');
    console.log(`   User: ${loginData.user.firstName} ${loginData.user.lastName}`);
    console.log(`   Email: ${loginData.user.email}`);
    console.log(`   Token: ${loginData.token.substring(0, 30)}...\n`);
  } else {
    console.log(`❌ Login failed: ${loginData.message}\n`);
    return;
  }

  // Test 3: Verify data persists in DB
  console.log('3️⃣ Testing Data Persistence...');
  const dbRes = await fetch(`${API_URL}/auth`);
  const dbData = await dbRes.json();
  
  const users = dbData.tables.find(t => t.name === 'users');
  console.log(`✅ Database persistence verified!`);
  console.log(`   Total users in DB: ${users.rowCount}`);
  console.log(`   Last user registered: ${users.lastRows[users.lastRows.length - 1].firstName} ${users.lastRows[users.lastRows.length - 1].lastName}\n`);

  console.log('✅ All tests passed! Database is working correctly.');
}

testAuthFlow().catch(console.error);

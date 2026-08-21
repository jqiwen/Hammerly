import sqlite3 from 'sqlite3';
import path from 'path';

const dbPath = path.join(process.cwd(), 'data/hammerly.db');

const db = new sqlite3.Database(dbPath, (err) => {
  if (err) {
    console.error('❌ Database error:', err);
    process.exit(1);
  }

  console.log('✅ Connected to database\n');

  // Get all users
  db.all('SELECT id, firstName, lastName, email, createdAt FROM users', (err, rows) => {
    if (err) {
      console.error('❌ Query error:', err);
      db.close();
      process.exit(1);
    }

    if (!rows || rows.length === 0) {
      console.log('📋 No users found in database');
    } else {
      console.log(`📋 Found ${rows.length} user(s):\n`);
      rows.forEach((user, index) => {
        console.log(`User ${index + 1}:`);
        console.log(`  ID: ${user.id}`);
        console.log(`  Name: ${user.firstName} ${user.lastName}`);
        console.log(`  Email: ${user.email}`);
        console.log(`  Registered: ${user.createdAt}`);
        console.log('');
      });
    }

    // Check password hashing
    db.all('SELECT email, password FROM users', (err, rows) => {
      if (err) {
        console.error('❌ Query error:', err);
        db.close();
        process.exit(1);
      }

      console.log('🔐 Password Validation:');
      rows.forEach((user, index) => {
        const isHashed = user.password.startsWith('$2a$') || user.password.startsWith('$2b$');
        const status = isHashed ? '✅ Hashed (bcrypt)' : '❌ NOT hashed (plain text!)';
        console.log(`  ${index + 1}. ${user.email}: ${status}`);
      });

      db.close();
      console.log('\n✅ Verification complete!');
    });
  });
});

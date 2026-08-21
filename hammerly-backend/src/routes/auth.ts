import { Router, Request, Response } from 'express';
import { getOne, getAll, runInsert } from '../db/database.js';
import { hashPassword, comparePassword, generateToken } from '../utils/auth.js';
import { authMiddleware } from '../middleware/auth.js';

const router = Router();
const DEFAULT_AVATAR_IMAGE = '/images/user.jpg';

interface User {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  createdAt: string;
}


/**
 * @swagger
 * /api/auth/register:
 *   post:
 *     tags:
 *       - Auth
 *     summary: User registration
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             properties:
 *               email:
 *                 type: string
 *                 description: User email
 *               password:
 *                 type: string
 *                 description: User password
 *               firstName:
 *                 type: string
 *                 description: User's first name
 *               lastName:
 *                 type: string
 *                 description: User's last name
 *               confirmPassword:
 *                 type: string
 *                 description: Confirmation of the user password
 *               phone:
 *                 type: string
 *                 description: User's phone number
 *     responses:
 *       201:
 *         description: Registration successful
 *       400:
 *         description: Invalid input data
 */

// POST register
router.post('/register', async (req: Request, res: Response) => {
  const { email, password, firstName, lastName, phone } = req.body;

  if (!email || !password || !firstName || !lastName || !phone) {
    return res.status(400).json({
      success: false,
      message: 'All fields are required',
    });
  }

  try {
    const existingUser = await getOne('SELECT * FROM users WHERE email = ?', [email]);
    if (existingUser) {
      return res.status(400).json({
        success: false,
        message: 'Email is already in use',
      });
    }

    const hashedPassword = await hashPassword(password);
    const userId = await runInsert(
      'INSERT INTO users (email, password, firstName, lastName, phone, avatarImage) VALUES (?, ?, ?, ?, ?, ?)',
      [email, hashedPassword, firstName, lastName, phone, DEFAULT_AVATAR_IMAGE]
    );

    const token = generateToken(userId, email);

    res.status(201).json({
      success: true,
      message: 'User registered successfully',
      token,
      user: {
        id: userId,
        firstName,
        lastName,
        email,
        phone,
        avatarImage: DEFAULT_AVATAR_IMAGE,
      }
    });
  } catch (error) {
    console.error('Error during registration:', error);
    res.status(500).json({
      success: false,
      message: 'Internal server error',
    });
  }
});

/**
 * @swagger
 * /api/auth/login:
 *   post:
 *     tags:
 *       - Auth
 *     summary: User login
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             properties:
 *               email:
 *                 type: string
 *                 description: User email
 *               password:
 *                 type: string
 *                 description: User password
 *     responses:
 *       200:
 *         description: Login successful
 *       401:
 *         description: Invalid credentials
 */

// POST login
router.post('/login', async (req: Request, res: Response) => {
  try {
    const { email, password } = req.body;

    // Validation
    if (!email || !password) {
      return res.status(400).json({
        success: false,
        message: 'Email and password are required'
      });
    }

    // Find user
    const user = await getOne<User>('SELECT * FROM users WHERE email = ?', [email]);
    if (!user) {
      return res.status(401).json({
        success: false,
        message: 'Invalid email or password'
      });
    }

    // Verify password
    const isPasswordValid = await comparePassword(password, user.password);
    if (!isPasswordValid) {
      return res.status(401).json({
        success: false,
        message: 'Invalid email or password'
      });
    }

    // Generate token
    const token = generateToken(user.id, user.email);

    res.json({
      success: true,
      message: 'Login successful',
      token,
      user: {
        id: user.id,
        firstName: user.firstName,
        lastName: user.lastName,
        email: user.email,
        phone: (user as any).phone || '',
        avatarImage: (user as any).avatarImage || DEFAULT_AVATAR_IMAGE
      }
    });
  } catch (error) {
    console.error('Login error:', error);
    res.status(500).json({
      success: false,
      message: 'Internal server error'
    });
  }
});

/**
 * @swagger
 * /api/auth/logout:
 *   post:
 *     tags:
 *       - Auth
 *     summary: User logout
 *     description: Logs out the user by invalidating their session or token.
 *     responses:
 *       200:
 *         description: Logout successful
 */

// POST logout (client-side token removal, but we can still provide endpoint)
router.post('/logout', authMiddleware, (req: Request, res: Response) => {
  // Token verification happens in middleware
  // Client should remove token from storage
  res.json({
    success: true,
    message: 'Logout successful'
  });
});

/**
 * @swagger
 * /api/auth/:
 *   get:
 *     tags:
 *       - Auth
 *     summary: Debug endpoint
 *     description: Shows all database tables information and the last 10 rows.
 *     responses:
 *       200:
 *         description: Debug information retrieved successfully
 */
// GET debug endpoint - show all database tables info and last 10 rows
router.get('/', async (req: Request, res: Response) => {
  try {
    // Get all table names
    const tables = await getAll<{ name: string }>(
      "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"
    );

    const dbInfo: any = {
      success: true,
      tables: [],
      totalTables: tables.length
    };

    // Get info for each table
    for (const table of tables) {
      const tableName = table.name;
      
      // Get table schema
      const schema = await getAll<any>(`PRAGMA table_info(${tableName})`);
      
      // Get last 10 rows
      const rows = await getAll<any>(`SELECT * FROM ${tableName} ORDER BY id DESC LIMIT 10`);
      
      // Get row count
      const count = await getOne<{ count: number }>(
        `SELECT COUNT(*) as count FROM ${tableName}`
      );

      dbInfo.tables.push({
        name: tableName,
        rowCount: count?.count || 0,
        columns: schema.map((col: any) => ({
          name: col.name,
          type: col.type,
          notnull: col.notnull,
          primaryKey: col.pk
        })),
        lastRows: rows.reverse() // Show oldest first (reverse to show 10 oldest)
      });
    }

    res.json(dbInfo);
  } catch (error) {
    console.error('Database info error:', error);
    res.status(500).json({
      success: false,
      message: 'Error fetching database info',
      error: (error as Error).message
    });
  }
});





export default router;

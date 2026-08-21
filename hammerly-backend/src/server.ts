import express from 'express';
import cors from 'cors';
import dotenv from 'dotenv';
import auctionRoutes from './routes/auctions.js';
import authRoutes from './routes/auth.js';
import usersRoutes from './routes/users.js';
import { initializeDatabase } from './db/init.js';
import swaggerJsDoc from 'swagger-jsdoc';
import swaggerUi from 'swagger-ui-express';

dotenv.config();

const app = express();
const PORT = process.env.PORT || 5000;
const FRONTEND_URL = process.env.FRONTEND_URL || 'http://localhost:3000';

// Initialize database
await initializeDatabase();

// Middleware
app.use(cors({
  origin: FRONTEND_URL,
  credentials: true,
}));

app.use(express.json({ limit: '20mb' }));
app.use(express.urlencoded({ extended: true, limit: '20mb' }));

// Swagger configuration
const swaggerOptions = {
  swaggerDefinition: {
    openapi: '3.0.0',
    info: {
      title: 'Hammerly API',
      version: '1.0.0',
      description: 'API documentation for the Hammerly auction platform',
    },
    servers: [
      {
        url: 'http://localhost:5000',
        description: 'Development server',
      },
    ],
    components: {
      securitySchemes: {
        BearerAuth: {
          type: 'http',
          scheme: 'bearer',
          bearerFormat: 'JWT',
          description: 'Enter your JWT token'
        }
      }
    },
    tags: [
      {
        name: 'Auctions',
        description: 'APIs related to auction operations',
      },
      {
        name: 'Auth',
        description: 'APIs related to authentication operations',
      },
      {
        name: 'Users',
        description: 'APIs related to user operations',
      },
    ],
  },
  apis: ['./src/routes/*.ts'], // Path to the API docs
};

const swaggerDocs = swaggerJsDoc(swaggerOptions);
app.use('/api-docs', swaggerUi.serve, swaggerUi.setup(swaggerDocs));


// Routes
app.use('/api/auctions', auctionRoutes);
app.use('/api/auth', authRoutes);
app.use('/api/users', usersRoutes);

// Health check
app.get('/health', (req, res) => {
  res.json({ status: 'Backend is running' });
});

// Start server
app.listen(PORT, () => {
  console.log(`✨ Server running on http://localhost:${PORT}`);
  console.log(`📝 Frontend connected from: ${FRONTEND_URL}`);
});

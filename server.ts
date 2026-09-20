import express from 'express';
import path from 'path';
import { createServer as createViteServer } from 'vite';

async function startServer() {
  const app = express();
  const PORT = 3000;

  app.use(express.json());

  // Hugging Face Semantic Search API proxy for category and query filtering
  app.get('/api/spaces/semantic-search', async (req, res) => {
    try {
      const category = (req.query.category as string) || '';
      const q = (req.query.q as string) || (req.query.query as string) || '';
      const includeNonRunning = req.query.includeNonRunning ?? 'true';

      let targetUrl = `https://huggingface.co/api/spaces/semantic-search?includeNonRunning=${includeNonRunning}`;
      if (category) {
        targetUrl += `&category=${encodeURIComponent(category)}`;
      }
      if (q) {
        targetUrl += `&q=${encodeURIComponent(q)}`;
      }

      const upstream = await fetch(targetUrl, {
        headers: {
          'Accept': 'application/json',
          'User-Agent': 'HF-Spaces-Android-Explorer/1.0',
        },
      });

      if (!upstream.ok) {
        return res.status(upstream.status).json({
          error: `Hugging Face Semantic Search API returned HTTP ${upstream.status}: ${upstream.statusText}`,
        });
      }

      const data = await upstream.json();
      res.setHeader('Cache-Control', 'public, max-age=60');
      return res.json(data);
    } catch (err: any) {
      console.error('Error proxying Hugging Face Semantic Search API:', err);
      return res.status(500).json({
        error: err.message || 'Failed to fetch semantic search from Hugging Face',
      });
    }
  });

  // Hugging Face Spaces API proxy to bypass browser cross-origin limits
  app.get('/api/spaces', async (req, res) => {
    try {
      const page = req.query.p || '0';
      const includeNonRunning = req.query.includeNonRunning ?? 'true';
      const withCount = req.query.withCount ?? 'false';
      const filter = (req.query.filter as string) || '';
      const search = (req.query.search as string) || '';

      let targetUrl = `https://huggingface.co/spaces-json?p=${page}&includeNonRunning=${includeNonRunning}&withCount=${withCount}`;
      if (filter) {
        targetUrl += `&filter=${encodeURIComponent(filter)}`;
      }
      if (search) {
        targetUrl += `&search=${encodeURIComponent(search)}`;
      }

      const upstream = await fetch(targetUrl, {
        headers: {
          'Accept': 'application/json',
          'User-Agent': 'HF-Spaces-Android-Explorer/1.0',
        },
      });

      if (!upstream.ok) {
        return res.status(upstream.status).json({
          error: `Hugging Face API returned HTTP ${upstream.status}: ${upstream.statusText}`,
        });
      }

      const data = await upstream.json();
      res.setHeader('Cache-Control', 'public, max-age=60');
      return res.json(data);
    } catch (err: any) {
      console.error('Error proxying Hugging Face Spaces API:', err);
      return res.status(500).json({
        error: err.message || 'Failed to fetch spaces from Hugging Face',
      });
    }
  });

  // Vite middleware for development vs static build in production
  if (process.env.NODE_ENV !== 'production') {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), 'dist');
    app.use(express.static(distPath));
    app.get('*', (req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`Server running on http://localhost:${PORT}`);
  });
}

startServer();

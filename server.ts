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

  // Hugging Face Space Info & API Endpoint Specification proxy
  app.get('/api/space-info', async (req, res) => {
    try {
      const spaceId = req.query.spaceId as string;
      if (!spaceId) {
        return res.status(400).json({ error: 'Missing required query parameter: spaceId' });
      }

      const authToken = (req.headers.authorization?.replace('Bearer ', '') || req.query.token as string) || '';
      const headers: Record<string, string> = {
        'Accept': 'application/json',
        'User-Agent': 'HF-Spaces-Android-Explorer/1.0',
      };
      if (authToken) {
        headers['Authorization'] = `Bearer ${authToken}`;
      }

      // Step 1: Fetch Space Metadata from HF Hub API
      const metaRes = await fetch(`https://huggingface.co/api/spaces/${spaceId}`, { headers });
      if (!metaRes.ok) {
        return res.status(metaRes.status).json({
          error: `Failed to fetch space metadata for ${spaceId}: HTTP ${metaRes.status}`,
        });
      }
      const meta = await metaRes.json();
      const subdomain = meta.subdomain || spaceId.replace('/', '-').toLowerCase();
      const host = meta.host || `https://${subdomain}.hf.space`;

      // Step 2: Fetch Gradio API endpoint info from the space host
      let endpoints: any[] = [];
      let rawInfo: any = null;

      try {
        const infoRes = await fetch(`${host}/gradio_api/info`, {
          headers,
          signal: AbortSignal.timeout(8000),
        });

        if (infoRes.ok) {
          rawInfo = await infoRes.json();
          if (rawInfo?.named_endpoints) {
            endpoints = Object.entries(rawInfo.named_endpoints).map(([name, ep]: [string, any]) => ({
              name,
              parameters: ep.parameters || [],
              returns: ep.returns || [],
            }));
          } else if (rawInfo?.unnamed_endpoints) {
            endpoints = Object.entries(rawInfo.unnamed_endpoints).map(([name, ep]: [string, any]) => ({
              name: `/${name}`,
              parameters: ep.parameters || [],
              returns: ep.returns || [],
            }));
          }
        }
      } catch (e) {
        // Try fallback /info endpoint
        try {
          const fallbackRes = await fetch(`${host}/info`, {
            headers,
            signal: AbortSignal.timeout(5000),
          });
          if (fallbackRes.ok) {
            rawInfo = await fallbackRes.json();
            if (rawInfo?.named_endpoints) {
              endpoints = Object.entries(rawInfo.named_endpoints).map(([name, ep]: [string, any]) => ({
                name,
                parameters: ep.parameters || [],
                returns: ep.returns || [],
              }));
            }
          }
        } catch {}
      }

      return res.json({
        spaceId,
        subdomain,
        host,
        sdk: meta.sdk,
        cardData: meta.cardData,
        runtime: meta.runtime,
        endpoints,
        rawInfo,
      });
    } catch (err: any) {
      console.error('Error fetching space info:', err);
      return res.status(500).json({ error: err.message || 'Failed to fetch space info' });
    }
  });

  // Hugging Face Space Predict / Run execution proxy
  app.post('/api/space-run', async (req, res) => {
    try {
      const { spaceId, endpoint, data, token, subdomain: providedSubdomain } = req.body;
      if (!spaceId && !providedSubdomain) {
        return res.status(400).json({ error: 'Missing spaceId or subdomain' });
      }

      const authToken = token || req.headers.authorization?.replace('Bearer ', '') || '';
      let subdomain = providedSubdomain;

      if (!subdomain && spaceId) {
        try {
          const metaRes = await fetch(`https://huggingface.co/api/spaces/${spaceId}`, {
            headers: authToken ? { Authorization: `Bearer ${authToken}` } : {},
          });
          if (metaRes.ok) {
            const meta = await metaRes.json();
            subdomain = meta.subdomain || spaceId.replace('/', '-').toLowerCase();
          }
        } catch {
          subdomain = spaceId.replace('/', '-').toLowerCase();
        }
      }

      const host = `https://${subdomain}.hf.space`;
      const epName = (endpoint || '/predict').startsWith('/') ? endpoint : `/${endpoint}`;

      const callHeaders: Record<string, string> = {
        'Content-Type': 'application/json',
        'User-Agent': 'HF-Spaces-Android-Explorer/1.0',
      };
      if (authToken) {
        callHeaders['Authorization'] = `Bearer ${authToken}`;
      }

      // Try Gradio 4/5/6 call queue endpoint first
      const callRes = await fetch(`${host}/gradio_api/call${epName}`, {
        method: 'POST',
        headers: callHeaders,
        body: JSON.stringify({ data }),
        signal: AbortSignal.timeout(30000),
      });

      if (callRes.ok) {
        const callData = await callRes.json();
        const eventId = callData.event_id;

        if (eventId) {
          // Poll / stream the event stream for completion
          const sseRes = await fetch(`${host}/gradio_api/call${epName}/${eventId}`, {
            headers: authToken ? { Authorization: `Bearer ${authToken}` } : {},
            signal: AbortSignal.timeout(120000), // allow up to 2 min for model inference
          });

          if (sseRes.ok && sseRes.body) {
            const reader = sseRes.body.getReader();
            const decoder = new TextDecoder();
            let buffer = '';
            let finalResult: any = null;
            let errorResult: string | null = null;

            while (true) {
              const { done, value } = await reader.read();
              if (done) break;
              buffer += decoder.decode(value, { stream: true });

              const lines = buffer.split('\n');
              buffer = lines.pop() || '';

              for (let i = 0; i < lines.length; i++) {
                const line = lines[i].trim();
                if (line.startsWith('event: complete')) {
                  const nextLine = lines[i + 1] || '';
                  if (nextLine.startsWith('data: ')) {
                    try {
                      finalResult = JSON.parse(nextLine.slice(6));
                    } catch {}
                  }
                } else if (line.startsWith('event: error')) {
                  const nextLine = lines[i + 1] || '';
                  if (nextLine.startsWith('data: ')) {
                    try {
                      const parsed = JSON.parse(nextLine.slice(6));
                      errorResult = parsed.error || JSON.stringify(parsed);
                    } catch {
                      errorResult = nextLine.slice(6);
                    }
                  }
                }
              }

              if (finalResult || errorResult) break;
            }

            if (errorResult) {
              return res.status(400).json({ error: errorResult });
            }

            if (finalResult) {
              return res.json({ success: true, data: finalResult, host });
            }
          }
        }
      }

      // Fallback: Try classic Gradio /api/predict
      const fallbackRes = await fetch(`${host}/api/predict`, {
        method: 'POST',
        headers: callHeaders,
        body: JSON.stringify({ fn_index: 0, data }),
        signal: AbortSignal.timeout(90000),
      });

      if (fallbackRes.ok) {
        const result = await fallbackRes.json();
        return res.json({ success: true, data: result.data || result, host });
      }

      const errorText = await callRes.text();
      return res.status(callRes.status || 500).json({
        error: `Space call failed: HTTP ${callRes.status} - ${errorText.slice(0, 300)}`,
      });
    } catch (err: any) {
      console.error('Error running space:', err);
      return res.status(500).json({ error: err.message || 'Inference call failed' });
    }
  });

  // Proxy for uploading files (images, audio, etc) to Gradio spaces
  app.post('/api/space-upload', async (req, res) => {
    try {
      const { subdomain, files, token } = req.body;
      if (!subdomain || !files || !Array.isArray(files)) {
        return res.status(400).json({ error: 'Missing subdomain or files array' });
      }

      const host = `https://${subdomain}.hf.space`;
      const formData = new FormData();

      for (let i = 0; i < files.length; i++) {
        const item = files[i];
        if (item.base64) {
          // Data URL conversion
          const matches = item.base64.match(/^data:([A-Za-z-+\/]+);base64,(.+)$/);
          if (matches && matches.length === 3) {
            const buffer = Buffer.from(matches[2], 'base64');
            const blob = new Blob([buffer], { type: matches[1] });
            formData.append('files', blob, item.name || `file_${i}.png`);
          }
        }
      }

      const uploadHeaders: Record<string, string> = {
        'User-Agent': 'HF-Spaces-Android-Explorer/1.0',
      };
      if (token) {
        uploadHeaders['Authorization'] = `Bearer ${token}`;
      }

      const uploadRes = await fetch(`${host}/gradio_api/upload`, {
        method: 'POST',
        headers: uploadHeaders,
        body: formData,
      });

      if (!uploadRes.ok) {
        return res.status(uploadRes.status).json({
          error: `Upload failed: HTTP ${uploadRes.status}`,
        });
      }

      const paths = await uploadRes.json();
      return res.json({ success: true, paths });
    } catch (err: any) {
      console.error('Error uploading file to space:', err);
      return res.status(500).json({ error: err.message || 'File upload failed' });
    }
  });

  // Validate Hugging Face User Access Token via whoami-v2
  app.post('/api/validate-token', async (req, res) => {
    try {
      const { token } = req.body;
      if (!token) {
        return res.status(400).json({ error: 'Missing token' });
      }

      const whoRes = await fetch('https://huggingface.co/api/whoami-v2', {
        headers: {
          'Authorization': `Bearer ${token}`,
          'User-Agent': 'HF-Spaces-Android-Explorer/1.0',
        },
      });

      if (!whoRes.ok) {
        return res.status(401).json({
          valid: false,
          error: 'Invalid or expired Hugging Face token',
        });
      }

      const userData = await whoRes.json();
      return res.json({
        valid: true,
        user: {
          name: userData.name,
          fullname: userData.fullname,
          email: userData.email,
          plan: userData.plan,
          isPro: userData.isPro || userData.plan === 'pro',
          orgs: userData.orgs?.map((o: any) => o.name) || [],
        },
      });
    } catch (err: any) {
      return res.status(500).json({ error: err.message || 'Failed to validate token' });
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

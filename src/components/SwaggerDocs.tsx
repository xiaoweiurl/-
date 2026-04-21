'use client';

import React from 'react';
import dynamic from 'next/dynamic';
import 'swagger-ui-react/swagger-ui.css';

const SwaggerUI = dynamic(() => import('swagger-ui-react'), { ssr: false });

interface SwaggerDocsProps {
  spec: Record<string, unknown>;
}

export default function SwaggerDocs({ spec }: SwaggerDocsProps) {
  return (
    <div className="w-full h-full">
      <SwaggerUI spec={spec} />
    </div>
  );
}

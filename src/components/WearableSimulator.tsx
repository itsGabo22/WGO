"use client";

import React from "react";

export function WearableSimulator({ children }: { children: React.ReactNode }) {
  return (
    <div className="relative w-[340px] h-[340px] bg-background rounded-full overflow-hidden shadow-2xl flex flex-col border-[4px] border-neutral/10">
      {children}
    </div>
  );
}

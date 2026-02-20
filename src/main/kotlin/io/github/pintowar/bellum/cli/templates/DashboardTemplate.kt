package io.github.pintowar.bellum.cli.templates

object DashboardTemplate {
    fun generateHtml(jsonData: String): String =
        """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Bellum Interactive Dashboard</title>
    <!-- React & ReactDOM -->
    <script crossorigin src="https://unpkg.com/react@18/umd/react.production.min.js"></script>
    <script crossorigin src="https://unpkg.com/react-dom@18/umd/react-dom.production.min.js"></script>
    <!-- Babel standalone for JSX -->
    <script src="https://unpkg.com/@babel/standalone/babel.min.js"></script>
    <!-- Apache ECharts -->
    <script src="https://cdn.jsdelivr.net/npm/echarts@5.5.0/dist/echarts.min.js"></script>
    <!-- Google Fonts -->
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg-body: #f4f2ef;
            --bg-card: #ffffff;
            --text-main: #4a3b32;
            --text-muted: #95877a;
            --accent-primary: #d6913c;
            --accent-secondary: #54c597;
            --border-color: #e8dfd8;
        }
        body { font-family: "Inter", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; background: var(--bg-body); color: var(--text-main); margin: 0; padding: 30px; }
        .dashboard { max-width: 1400px; margin: 0 auto; display: flex; flex-direction: column; gap: 24px; }
        .card { background: var(--bg-card); padding: 24px 32px; border-radius: 12px; box-shadow: 0 8px 24px rgba(74, 59, 50, 0.04); border: 1px solid var(--border-color); }
        .row { display: flex; gap: 24px; flex-wrap: wrap; }
        .col-2 { flex: 2; min-width: 500px; }
        .col-1 { flex: 1; min-width: 300px; }
        table { width: 100%; border-collapse: collapse; margin-top: 10px; font-size: 0.9em; }
        th, td { border-bottom: 1px solid var(--border-color); padding: 14px 8px; text-align: left; }
        th { color: var(--text-muted); font-weight: 600; text-transform: uppercase; font-size: 0.75rem; letter-spacing: 0.05em; border-bottom: 2px solid var(--border-color); }
        h2 { margin-top: 0; font-size: 1.1rem; color: var(--accent-primary); font-weight: 700; margin-bottom: 20px; }
        .tag { font-size: 0.75rem; padding: 4px 10px; border-radius: 20px; font-weight: 600; background: var(--border-color); color: var(--text-main); }
        .tag.valid { background: #e3f5ec; color: #2e8b57; }
        .tag.warn { background: #fdf3e1; color: #b87a20; }
        
        input[type=range] { -webkit-appearance: none; width: 100%; background: transparent; margin: 15px 0; }
        input[type=range]:focus { outline: none; }
        input[type=range]::-webkit-slider-thumb { -webkit-appearance: none; height: 20px; width: 20px; border-radius: 50%; background: var(--bg-card); cursor: pointer; margin-top: -8px; border: 4px solid var(--accent-primary); box-shadow: 0 2px 5px rgba(0,0,0,0.2); }
        input[type=range]::-webkit-slider-runnable-track { width: 100%; height: 4px; cursor: pointer; background: var(--accent-primary); border-radius: 2px; }
        
        .header-title { font-size: 1.5rem; font-weight: 700; color: var(--text-main); margin-bottom: 4px; display: flex; align-items: center; gap: 10px; }
        .header-subtitle { font-size: 0.85rem; font-weight: 600; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.05em; }
    </style>
</head>
<body>
    <div id="root"></div>

    <script>
        window.BELLUM_DATA = $jsonData;
    </script>
    
    <script type="text/babel">
        const { useState, useEffect, useRef } = React;

        const parseDurationMatch = (ptDuration) => {
            // Very simple PT duration parser (e.g., PT30M, PT2H, PT1H30M)
            let mins = 0;
            const hoursMatch = ptDuration.match(/(\d+)H/);
            const minsMatch = ptDuration.match(/(\d+)M/);
            if (hoursMatch) mins += parseInt(hoursMatch[1], 10) * 60;
            if (minsMatch) mins += parseInt(minsMatch[1], 10);
            return mins;
        }

        const parseDuration = (dur) => {
            if (!dur) return 0;
            if (typeof dur === 'string' && dur.startsWith('PT')) {
                return parseDurationMatch(dur);
            }
            return 0; // fallback if numbers or different formats
        }

        const GanttChart = ({ solution, project }) => {
            const chartRef = useRef(null);

            useEffect(() => {
                const myChart = echarts.init(chartRef.current);
                myChart.setOption({ backgroundColor: 'transparent' }); // align with our theme
                
                const employees = project.employees.map(e => e.name);
                
                const dataPairs = [];
                const arrows = [];
                const priorityColors = { 'CRITICAL': '#e74c3c', 'MAJOR': '#3498db', 'MINOR': '#2ecc71' };

                const tasksById = {};
                project.tasks.forEach(t => { tasksById[t.id] = t; });

                project.tasks.forEach((t, i) => {
                    if (t.employee && t.startAt) {
                        const start = new Date(t.startAt).getTime();
                        const dur = parseDuration(t.duration) * 60000;
                        const end = start + dur;
                        const empIndex = employees.indexOf(t.employee.name);
                        
                        let label = t.name.replace('Task ', 'T');
                        const pred = t.dependsOn ? tasksById[t.dependsOn] : null;

                        if (pred) {
                            const predLabel = pred.name.replace('Task ', 'T');
                            label = predLabel + '->' + label;

                            if (pred.startAt && pred.employee) {
                                const predEnd = new Date(pred.startAt).getTime() + parseDuration(pred.duration) * 60000;
                                const predEmpIndex = employees.indexOf(pred.employee.name);
                                arrows.push([
                                    { coord: [predEnd, predEmpIndex] },
                                    { coord: [start, empIndex] }
                                ]);
                            }
                        }

                        const color = priorityColors[t.priority] || '#54c597';

                        dataPairs.push({
                            name: t.name,
                            value: [
                                empIndex,
                                start,
                                end,
                                parseDuration(t.duration),
                                label
                            ],
                            itemStyle: { normal: { color: color } }
                        });
                    }
                });

                function renderItem(params, api) {
                    const categoryIndex = api.value(0);
                    const start = api.coord([api.value(1), categoryIndex]);
                    const end = api.coord([api.value(2), categoryIndex]);
                    const height = api.size([0, 1])[1] * 0.6;
                    const labelText = api.value(4);

                    // Ensure minimum width to be visible
                    let width = end[0] - start[0];
                    if (width < 2) width = 2; 

                    const rectShape = echarts.graphic.clipRectByRect({
                        x: start[0],
                        y: start[1] - height / 2,
                        width: width,
                        height: height
                    }, {
                        x: params.coordSys.x,
                        y: params.coordSys.y,
                        width: params.coordSys.width,
                        height: params.coordSys.height
                    });

                    if (rectShape) {
                        rectShape.r = 4;
                        return {
                            type: 'group',
                            children: [
                                {
                                    type: 'rect',
                                    transition: ['shape'],
                                    shape: rectShape,
                                    style: api.style()
                                },
                                {
                                    type: 'text',
                                    style: {
                                        text: width > 20 ? labelText : '',
                                        x: rectShape.x + rectShape.width / 2,
                                        y: rectShape.y + rectShape.height / 2,
                                        textVerticalAlign: 'middle',
                                        textAlign: 'center',
                                        fill: '#ffffff',
                                        fontSize: 10,
                                        fontWeight: 600,
                                        fontFamily: 'Inter, sans-serif'
                                    },
                                    z2: 10
                                }
                            ]
                        };
                    }
                }

                const option = {
                    tooltip: {
                        formatter: function (params) {
                            return params.marker + params.name + ': ' + params.value[3] + ' min';
                        }
                    },
                    dataZoom: [
                        { type: 'slider', filterMode: 'weakFilter', showDataShadow: false, bottom: 0, height: 20 }, 
                        { type: 'inside', filterMode: 'weakFilter' }
                    ],
                    grid: { height: 300, top: 40, bottom: 40 },
                    xAxis: {
                        type: 'time',
                        scale: true,
                        axisLine: { lineStyle: { color: '#e8dfd8' } },
                        splitLine: { lineStyle: { color: '#f4f2ef' } },
                        axisLabel: {
                            color: '#95877a',
                            formatter: function (val) {
                                // show relative time or formatted date
                                const d = new Date(val);
                                return echarts.format.formatTime('hh:mm', d);
                            }
                        }
                    },
                    yAxis: {
                        data: employees,
                        axisLine: { show: false },
                        axisTick: { show: false },
                        axisLabel: { color: '#4a3b32', fontWeight: 600 }
                    },
                    series: [{
                        type: 'custom',
                        renderItem: renderItem,
                        encode: {
                            x: [1, 2],
                            y: 0
                        },
                        data: dataPairs,
                        markLine: {
                            symbol: ['none', 'arrow'],
                            symbolSize: [6, 12],
                            label: { show: false },
                            lineStyle: {
                                color: '#8e44ad',
                                width: 2,
                                type: 'solid'
                            },
                            data: arrows,
                            animation: false,
                            z: 20
                        }
                    }]
                };

                myChart.setOption(option, true);

                const handleResize = () => myChart.resize();
                window.addEventListener('resize', handleResize);
                return () => {
                    window.removeEventListener('resize', handleResize);
                    myChart.dispose();
                };
            }, [solution, project]);

            return <div ref={chartRef} style={{ width: '100%', height: '400px' }}></div>;
        };

        const ObjectiveChart = ({ history, currentIndex }) => {
            const chartRef = useRef(null);

            useEffect(() => {
                if (!chartRef.current || !history) return;
                const myChart = echarts.init(chartRef.current);
                myChart.setOption({ backgroundColor: 'transparent' });

                const maxDurations = history.map(h => parseDuration(h.maxDuration));
                const priorityCosts = history.map(h => h.priorityCost);
                const xData = history.map((_, i) => i);

                const option = {
                    tooltip: { trigger: 'axis' },
                    legend: { data: ['Max Duration (m)', 'Priority Cost'], textStyle: { color: '#95877a' } },
                    grid: { height: 200, bottom: 30, left: 50, right: 50, top: 40 },
                    animation: false,
                    xAxis: { 
                        type: 'category', 
                        data: xData,
                        axisLine: { lineStyle: { color: '#e8dfd8' } },
                        axisLabel: { color: '#95877a' }
                    },
                    yAxis: [
                        { type: 'value', name: 'Duration (m)', splitLine: { lineStyle: { color: '#f4f2ef' } }, axisLabel: { color: '#95877a' }, nameTextStyle: { color: '#95877a' } }, 
                        { type: 'value', name: 'Cost', splitLine: { show: false }, axisLabel: { color: '#95877a' }, nameTextStyle: { color: '#95877a' } }
                    ],
                    series: [
                        { 
                            name: 'Max Duration (m)', 
                            type: 'line', 
                            smooth: true,
                            symbol: 'none',
                            lineStyle: { width: 4 },
                            data: maxDurations, 
                            itemStyle: { color: '#d6913c' },
                            markLine: {
                                symbol: ['none', 'none'],
                                label: { show: false },
                                lineStyle: { color: '#d6913c', type: 'dashed', width: 2 },
                                data: [{ xAxis: currentIndex }]
                            } 
                        },
                        { 
                            name: 'Priority Cost', 
                            type: 'line', 
                            smooth: true,
                            symbol: 'none',
                            lineStyle: { width: 3 },
                            yAxisIndex: 1, 
                            data: priorityCosts,
                            itemStyle: { color: '#54c597' }
                        }
                    ]
                };

                myChart.setOption(option);

                const handleResize = () => myChart.resize();
                window.addEventListener('resize', handleResize);
                return () => {
                    window.removeEventListener('resize', handleResize);
                    myChart.dispose();
                };
            }, [history, currentIndex]);

            return <div ref={chartRef} style={{ width: '100%', height: '300px' }}></div>;
        };

        const App = () => {
            const data = window.BELLUM_DATA;
            const [currentIndex, setCurrentIndex] = useState(data && data.solutions ? data.solutions.length - 1 : 0);

            if (!data || !data.solutions || data.solutions.length === 0) {
                return <div>No solution data available.</div>;
            }

            const activeProject = data.solutions[currentIndex];
            const activeStats = data.solutionHistory[currentIndex];
            const maxIdx = data.solutions.length - 1;
            
            const numAssigned = activeProject.tasks.filter(t => t.employee != null).length;
            const isScheduled = numAssigned === activeProject.tasks.length;
            const status = isScheduled ? 'SCHEDULED' : (numAssigned > 0 ? 'PARTIAL' : 'NONE');

            return (
                <div className="dashboard">
                    <div style={{ padding: '0 10px' }}>
                        <div className="header-title">
                            <span style={{ backgroundColor: 'var(--accent-primary)', color: 'white', padding: '4px 8px', borderRadius: '4px', fontSize: '0.8em' }}>S</span>
                            Solver Dashboard
                        </div>
                        <div className="header-subtitle">EVOLUTION & OPTIMIZATION</div>
                    </div>

                    <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: '5px' }}>
                        <div style={{ display: 'flex', alignItems: 'baseline', gap: '15px' }}>
                            <h2 style={{ marginBottom: 0 }}>Solution Evolution</h2>
                            {activeStats.optimal && <span style={{ color: 'var(--accent-primary)', fontWeight: 600, fontSize: '0.9rem' }}>Optimal Found</span>}
                        </div>
                        <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                            Current Iteration: <span style={{ color: 'var(--accent-primary)', fontWeight: 700 }}>{currentIndex}</span> / {maxIdx}
                        </div>
                        <div style={{ display: 'flex', gap: '20px', alignItems: 'center', marginTop: '10px' }}>
                            <input 
                                type="range" 
                                min="0" 
                                max={maxIdx} 
                                value={currentIndex} 
                                onChange={e => setCurrentIndex(parseInt(e.target.value, 10))} 
                                style={{ flex: 1 }}
                            />
                        </div>
                    </div>

                    <div className="row">
                        <div className="card col-2">
                            <h2>Resource Task Assignment</h2>
                            <GanttChart solution={activeStats} project={activeProject} />
                        </div>

                        <div className="card col-1">
                            <h2>Solution Statistics</h2>
                            <table>
                                <tbody>
                                    <tr><th>Metric</th><th>Value</th></tr>
                                    <tr><td>Status</td><td><span className={"tag " + (activeStats.valid ? "valid" : "warn")}>{status}</span></td></tr>
                                    <tr><td>Valid Schedule</td><td><span className={"tag " + (activeStats.valid ? "valid" : "warn")}>{activeStats.valid ? 'YES' : 'NO'}</span></td></tr>
                                    <tr><td>Max Duration</td><td style={{ fontWeight: 600 }}>{activeStats.maxDuration}</td></tr>
                                    <tr><td>Priority Cost</td><td style={{ fontWeight: 600 }}>{activeStats.priorityCost}</td></tr>
                                    <tr><td>Optimal Found</td><td>{activeStats.optimal ? 'YES' : 'NO'}</td></tr>
                                </tbody>
                            </table>
                        </div>
                    </div>

                    <div className="row">
                        <div className="card col-2">
                            <h2>Objective Trend <span style={{fontSize: '0.7em', color: 'var(--text-muted)', fontWeight: 400}}>MINIMIZING MAX SPAN</span></h2>
                            <ObjectiveChart history={data.solutionHistory} currentIndex={currentIndex} />
                        </div>

                        <div className="card col-1">
                            <h2>Solver Statistics</h2>
                            <table>
                                <tbody>
                                    <tr><th>Metric</th><th>Value</th></tr>
                                    {Object.entries(data.solverStats)
                                        .filter(([k, v]) => typeof v !== 'object' && k !== 'type')
                                        .map(([k, v]) => (
                                            <tr key={k}><td>{k.charAt(0).toUpperCase() + k.slice(1)}</td><td style={{ fontWeight: 600, color: 'var(--accent-primary)' }}>{v}</td></tr>
                                        ))
                                    }
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>
            );
        };

        const root = ReactDOM.createRoot(document.getElementById('root'));
        root.render(<App />);
    </script>
</body>
</html>"""
}
